(ns r3bot.chat
  (:require [r3bot.septa :as septa]
            [r3bot.state :refer [state]]
            [r3bot.fuzzy :as fuzzy]
            [r3bot.channels :as channels]
            [r3bot.telegram-api :as telegram]
            [cuerdas.core :as s]
            [clojure.core.async :as a])
  (:import [java.time ZoneId LocalDateTime LocalTime LocalDate Instant]))

(defn find-user [chat-id]
  (let [users (-> @state :r3bot.core/config :users)]
    (get users chat-id)))

(defn flip-map [{:keys [from to] :as m}]
  (assoc m :from to :to from))

(defn figure-out-stations [stations]
  (let [now (LocalDateTime/now (ZoneId/of "America/New_York"))
        noon (LocalDateTime/of (LocalDate/now) LocalTime/NOON)]
    (if (.isBefore now noon) stations (flip-map stations))))

(defn stations-for-user [chat-id]
  (-> chat-id
      find-user
      :default
      figure-out-stations))

(defn find-trains [stations]
  (let [c (a/chan 1 (map septa/parse-arrivals))]
    (-> stations
        (septa/next-to-arrive! (septa/async-setpa c)))
    c))

(defn find-train-views []
  (let [c (a/chan 1 (map septa/parse-train-view))]
    (septa/train-view! (septa/async-setpa c))
    c))

(defn filter-train-views [train-nums train-views]
  (filterv (set train-nums) train-views))

(defn find-trains-info [messages]
  (let [train->chat-id (into {} (map (fn [[chat-id {:keys [number-trains]}]]
                                       [(cond-> (stations-for-user chat-id)
                                                number-trains (assoc :number number-trains)
                                                true find-trains)
                                        chat-id])
                                     messages))]
    (a/go-loop [r {} trains (keys train->chat-id)]
      (if (empty? trains)
        r
        (let [[schedule ch] (a/alts! trains)]
          (recur (assoc r (get train->chat-id ch) schedule) (remove #{ch} trains)))))))

(defn parse-message-text [text]
  (let [t (s/trim text)]
    (if (s/numeric? t)
      (let [n (s/parse-long t)]
        (if (and (pos? n) (<= n 12))
          {:number-trains n :text t}
          {:text t}))
      {:text t})))

(defn parse-station-direction [text]
  (let [[from to] (drop 1 (s/split text))]
    (if (or (nil? from) (nil? to))
      [:error "Invalid command"]
      {:from (fuzzy/fuzzy-decide-station from)
       :to   (fuzzy/fuzzy-decide-station to)})))

(defn chat-poller! [{:keys [incoming]}]
  (let [c (a/chan)]
    (telegram/get-request! (telegram/query-offset @state) (telegram/async-telegram c))
    (a/go
      (while (channels/chat-latch! @state)
        (when-let [{:keys [result] :as response} (a/<! c)]
          (when-not (empty? result)
            (println (str (Instant/now) " got response... " response))
            (swap! state assoc ::telegram/offset (telegram/max-offset response))
            (let [regular-commands  (remove telegram/bot-command? result)
                  bot-commands      (filter telegram/bot-command? result)]
              (a/>! incoming {:command-type :regular :commands regular-commands})
              (a/>! incoming {:command-type :bot     :commands bot-commands})))
          (telegram/get-request! (telegram/query-offset @state) (telegram/async-telegram c)))))))

(defn chat-sender! [outgoing-ch]
  (let [c (a/chan)]
    (a/go
      (while (channels/chat-latch! @state)
        (let [[chat-id msg] (a/<! outgoing-ch)]
          (telegram/send-message! chat-id msg (telegram/async-telegram c))
          (a/<! c))))))

(defn format-direction [{:keys [from to]}]
  (let [s @state
        station-name #(septa/url-decode (get-in s [::septa/septa %]))
        from-station (station-name from)
        to-station   (station-name to)]
    (str "\uD83D\uDCCD " from-station " \uD83C\uDFC1 " to-station)))

(defn format-train-message [{:keys [departure-time arrival-time delay train-line train-number]}]
  (let [delay-decorator (condp = delay "On time" "✔️" "⌛")]
    (str departure-time " ➡ " arrival-time " " delay-decorator " " delay "\n" train-line " \uD83D\uDE8B " train-number)))

(defn listen-to-regular! [{:keys [regular outgoing]}]
    (println "Listening to regular...")
    (a/go
      (while (channels/chat-latch! @state)
        (let [{:keys [commands]} (a/<! regular)
              m (into {} (map (juxt telegram/chat-id (comp parse-message-text telegram/message-text)) commands))
              trains-info (a/<! (find-trains-info m))]
          (doseq [[chat-id [ok schedule]] trains-info]
            (a/>! outgoing [chat-id (format-direction (stations-for-user chat-id))])
            (if (= :ok ok)
              (a/>! outgoing [chat-id (s/join "\n\n" (map format-train-message schedule))])
              (a/>! outgoing [chat-id (str schedule)])))))))

(defn listen-to-bot! [{:keys [bot]}]
    (a/go
      (while (channels/chat-latch! @state)
        (let [msg (a/<! bot)]
          (println "Bot: " msg)
          ))))

(defn chat-cycle []
  (let [;incoming-ch (-> @state ::channels/chans :incoming)
        outgoing-ch (-> @state ::channels/chans :outgoing)
        chans (-> @state ::channels/chans)]
    (chat-poller! chans)
    (chat-sender! outgoing-ch)
    (listen-to-bot! chans)
    (listen-to-regular! chans)))
