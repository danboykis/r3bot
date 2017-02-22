(ns r3bot.core
  (:require
    [r3bot.septa :as septa]
    [r3bot.channels :as channels]
    [r3bot.telegram-api :as telegram]
    [r3bot.state :refer [state]]
    [abk.core :as abk]
    [cprop.core :refer [load-config] :as cprop]))

(defn init-config! [& args]
  (load-config))

(def dep-graph [:config
                :channels [:config]
                :telegram [:config]
                :septa [:config]])

(def inits {:config [init-config! identity ::config]
            :channels [channels/init-channels! channels/stop-channels! ::channels/chans]
            :telegram [telegram/init-telegram! identity ::telegram/telegram]
            :septa [septa/init-septa! identity ::septa/septa]})

(defn start-r3bot! []
  (reset! state (abk/start! {} inits dep-graph)))

(defn stop-r3bot! []
  (reset! state (abk/stop! @state inits dep-graph)))
