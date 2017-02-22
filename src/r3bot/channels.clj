(ns r3bot.channels
  (:require [r3bot.state :refer [state]]
            [r3bot.telegram-api :as telegram]
            [clojure.core.async :as a]))

(defn chat-latch! [s] (-> s ::chans :chat-latch deref))

(defn init-channels! [s]
  (let [incoming-ch (a/chan 10)
        ;publication (a/pub incoming-ch :command-type #(case % :bot (a/dropping-buffer 10) :regular (a/dropping-buffer 10)))
        bot-ch      (a/chan 10)
        outgoing-ch (a/chan 10)
        regular-ch (a/chan 10)
        chat-latch (atom true)]
    ;(a/sub publication :bot bot-ch)
    ;(a/sub publication :regular regular-ch)
    {:incoming incoming-ch :bot bot-ch :regular regular-ch :outgoing outgoing-ch :chat-latch chat-latch}))

(defn stop-channels! [s]
  (some-> s :chat-latch (reset! false))
  ;(some-> s :pub a/unsub-all)
  (doseq [k [:incoming :bot :regular :outgoing]]
    (some-> s k a/close!)))
