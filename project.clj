(defproject com.chartbeat.cljbeat/options "1.0.0"
  :description "Chartbeat specific option parsing utility functions."
  :url "https://github.com/chartbeat-labs/cljbeat-options"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/license/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [clj-yaml "0.4.0"]]

  :deploy-repositories [["releases" :clojars]]
  :signing {:gpg-key "F0903068"}

  :aot :all
  :vcs :git)
