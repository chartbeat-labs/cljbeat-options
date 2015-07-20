(defproject com.chartbeat.cljbeat/options "1.0.0-SNAPSHOT"
  :description "Chartbeat specific option parsing utility functions."
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [clj-yaml "0.4.0"]]

  :plugins [[s3-wagon-private "1.1.2"]]

  :repositories [["releases" {:url "https://jars.chartbeat.com:7443/releases/"}]
                 ["snapshots" {:url "https://jars.chartbeat.com:7443/snapshots/"}]]
  :deploy-repositories [["releases" {:url "s3p://chartbeat-jars/releases/"
                                     :sign-releases false}]
                        ["snapshots" {:url "s3p://chartbeat-jars/snapshots/"
                                      :sign-releases false}]]

  :aot :all
  :vcs :git)
