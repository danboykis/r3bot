(ns r3bot.telegram-api
  (:require [r3bot.state :refer [state]]
            [cuerdas.core :as string]
            [cheshire.core :refer [parse-string generate-string]]
            [org.httpkit.client :as http]
            [clojure.core.async :as a]
            [clojure.walk :refer [prewalk]]
            [clojure.string :as str])
  (:import [java.net URLEncoder]))

(defn url-encode [s] (URLEncoder/encode s))

(defn- nested-param [params]            ; code copyed from clj-http
  (prewalk (fn [d]
             (if (and (vector? d) (map? (second d)))
               (let [[fk m] d]
                 (reduce (fn [m [sk v]]
                           (assoc m (str (name fk) \[ (name sk) \]) v))
                         {} m))
               d))
           params))

(defn query-string
  "Returns URL-encoded query string for given params map."
  [m]
  (let [m (nested-param m)
        param (fn [k v]  (str (url-encode (name k)) "=" (url-encode v)))
        join  (fn [strs] (str/join "&" strs))]
    (join (for [[k v] m] (if (sequential? v)
                           (join (map (partial param k) (or (seq v) [""])))
                           (param k v))))))


(defn snake->kebab [k]
  (keyword (string/replace k "_" "-")))

(defn kebab->snake [k]
  (-> k name
      (string/replace "-" "_")))

(defn async-telegram [c]
  (fn [{:keys [body status error] :as r}]
    (if (and (= 200 status) (not error))
      (a/put! c (parse-string body snake->kebab))
      (a/put! c r))))

(defn send-message! [chat-id msg cb]
  (let [s @state
        body (query-string {:chat_id (str chat-id) :text msg})]
    (http/post (-> s ::telegram :send-message)
               {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                :query-params {:chat_id chat-id :text msg :parse_mode "markdown"}}
               cb)))

(defn request-timeout [s]
  (get-in s [:r3bot.core/config :telegram :http-prefs :long-poll-interval] 30000))

(defn query-offset [s]
  (some->> s ::offset inc (hash-map :offset)))

(defn get-request! [params cb]
  (let [s @state
        req-timeout (request-timeout s)]
    (http/get (-> s ::telegram :get-messages)
              {:timeout req-timeout :keepalive req-timeout
               :query-params (merge {:timeout req-timeout} params)}
              cb)))

(defn get-request-sync! [{:keys [query-params]}]
  (-> @(http/get (-> @state ::telegram :get-messages) {:timeout 2000 :query-params query-params})
      :body
      (parse-string snake->kebab)))

(defn first-message [updates]
  (-> updates :result first))

(defn last-message [updates]
  (-> updates :result last))

(defn chat-id [message]
  (or
    (get-in message [:message :chat :id])
    (get-in message [:edited-message :chat :id])))

(defn offset [message]
  (:update-id message))

(defn max-offset [{:keys [result]}]
  (apply max (map offset result)))

(defn chat-ids [result]
  (into #{} (map chat-id) result))

(defn- build-query [s k]
  (let [path [:r3bot.core/config :telegram :api]]
    (format (get-in s (conj path k))
            (get-in s (conj path :api-key)))))

(defn bot-command? [msg]
  (let [entities (concat (get-in msg [:message :entities])
                         (get-in msg [:edited-message :entities]))]
    (some #(= (:type %) "bot_command") entities)))

(defn message-text [msg]
  (or (get-in msg [:message :text])
      (get-in msg [:edited-message :text])))

(defn init-telegram! [s]
  {:get-messages (build-query s :get-updates)
   :send-message (build-query s :send-message)})
