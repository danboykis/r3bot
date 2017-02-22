(ns r3bot.channels
  (:require [r3bot.state :refer [state]]
            [r3bot.telegram-api :as telegram]
            [clojure.core.async :as a]))

(defn chat-latch! [s] (some-> s ::chans :chat-latch deref))

(defn init-channels! [s]
  (let [incoming-ch (a/chan 10)
        publication (a/pub incoming-ch :command-type #(case % :bot (a/dropping-buffer 10) :regular (a/dropping-buffer 10)))
        bot-ch      (a/chan 10 (remove (comp empty? :commands)))
        regular-ch  (a/chan 10 (remove (comp empty? :commands)))
        outgoing-ch (a/chan 10)
        telegram-ch (a/chan 10)
        chat-latch (atom true)]
    (a/sub publication :bot bot-ch)
    (a/sub publication :regular regular-ch)
    {:telegram telegram-ch :incoming incoming-ch :bot bot-ch :regular regular-ch :outgoing outgoing-ch :chat-latch chat-latch}))

(defn stop-channels! [s]
  (some-> s :chat-latch (reset! false))
  (doseq [k [:incoming :bot :regular :outgoing :telegram]]
    (some-> s k a/close!))
  (some-> s :pub a/unsub-all))
