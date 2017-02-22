(defproject r3bot "0.1.0-SNAPSHOT"
  :description "R3 Bot"
  :url "http://example.com/FIXME"
  :license {:name "Unlicense"
            :url "http://unlicense.org/"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [http-kit "2.2.0"]
                 [cheshire "5.6.3"]
                 [cprop "0.1.8"]
                 [com.danboykis/abk "0.1.0"]
                 [funcool/cuerdas "2.0.1"]
                 [org.clojure/core.async "0.2.395"]
                 [clj-fuzzy "0.3.3"]
                 [funcool/promesa "1.6.0"]]

  :profiles {:dev {:source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :dependencies [[throttler "1.0.0" :exclusions [org.clojure/clojure
                                                                  org.clojure/core.async]]]
                   }})
