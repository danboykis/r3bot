(ns r3bot.septa
  (:require [r3bot.state :refer [state]]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [clojure.core.async :as a]
            [clojure.set :as cs]
            [cuerdas.core :as ss]
            [clojure.spec :as s])
  (:import [java.net URLEncoder URLDecoder]))

(defn arrivals-url [] (-> @state :r3bot.core/config :septa :arrivals-url))
(defn train-view-url [] (-> @state :r3bot.core/config :septa :train-view-url))

(defn url-encode [string]
  (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(defn url-decode [string]
  (some-> string str (URLDecoder/decode "UTF-8")))

(defn async-setpa [ch]
  (fn [r] (a/put! ch r)))

(def arrivals-lookup {:orig_train :train-number
                      :orig_line :train-line
                      :orig_departure_time :departure-time
                      :orig_arrival_time :arrival-time
                      :orig_delay :delay
                      :isdirect :direct?})

(def train-view-lookup {:trainno :train-number
                        :nextstop :next-stop
                        :dest :destination
                        :SOURCE :source
                        :TRACK :track
                        :TRACK_CHANGE :track-change})

(defn cleanup-json-response [septa-original-response rename-lookup]
  (mapv #(cs/rename-keys % rename-lookup) septa-original-response))


(defn next-to-arrive! [{:keys [from to number] :or {number 8}} cb]
  (let [stations (::septa @state)]
    (assert (contains? stations from))
    (assert (contains? stations to))
    (http/get (str (format (arrivals-url) (stations from) (stations to)) "/" number) cb)))

(defn train-view! [cb]
  (http/get (train-view-url) cb))

(declare parse-septa-response)

(defn parse-train-view [response]
  (parse-septa-response response train-view-lookup ::train-statuses))

(defn parse-arrivals [response]
  (parse-septa-response response arrivals-lookup ::septa-arrivals))

(defn parse-septa-response [{:keys [status body] :as r} rename-lookup spec]
  (if (= 200 status)
    (let [response (-> body (parse-string true) (cleanup-json-response rename-lookup))
          septa-response (s/conform spec response)]
      (if (= ::s/invalid septa-response)
        (throw (ex-info "invalid response from septa api" (s/explain-data spec response)))
        [:ok septa-response]))
    [:error (select-keys r [:status :body :error])]))

;(defn format-septa-response [[status trains]]
;  (if (= :ok status)
;    (let [sw (java.io.StringWriter.)]
;      (binding [*out* sw]
;        (clojure.pprint/print-table (map #(dissoc % :direct?) trains)))
;      (.toString sw))
;    "Can't display schedules"))

    
(s/def ::septa-arrivals (s/coll-of ::septa-arrival))
(s/def ::septa-arrival (s/keys :req-un [::train-number ::train-line ::arrival-time ::delay ::direct?]))
(s/def ::train-number string?)
(s/def ::direct? (s/conformer #(Boolean/parseBoolean %)))

(s/def ::train-statuses (s/coll-of ::train-status))
(s/def ::train-status (s/keys :req-un [::next-stop ::late ::train-number ::lon ::lat]))

(defn init-septa! [s]
  (into {} (map (fn [[k v]] [k (url-encode v)])
                (-> s :r3bot.core/config :septa :stations))))
