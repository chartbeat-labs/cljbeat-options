(ns cb.cljbeat.options-test
  (:require [clojure.test :refer :all]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [cb.cljbeat.options :refer [parse-opts simple-spec sstypemap
                                        options global-specs
                                        set-options! add-option-specs!]]))

(def mock-yaml-file
"
int: 10
float: 1.5
list:
  - 1
  - 2
  - 3
")

(deftest test-parse-opts
  (let [mock-option-specs [["-f" "--foo NUMBER" ""
                            :parse-fn #(Integer/parseInt %)
                            :validate-fn number?]
                            ["-b" "--bar NUMBER" ""
                             :default 1
                             :parse-fn #(Integer/parseInt %)]]
        mock-help-args ["--help"]
        mock-invalid-args ["--foo=bar"]
        mock-valid-args ["--foo=10"]
        mock-valid-args-key :foo
        mock-valid-args-value 10
        mock-yaml-string "bar: 2"
        mock-yaml-arg-key :bar
        mock-yaml-arg-value 2]

      (testing "Returns nil when --help is passed."
        (with-redefs-fn {#'println (fn [& args] nil)}
          #(is (nil? (parse-opts mock-help-args mock-option-specs)))))

      (testing "Returns nil when invalid args are passed."
        (with-redefs-fn {#'cb.cljbeat.options/print-failure (fn [& args] nil)
                         #'cb.cljbeat.options/log-failure (fn [& args] nil)}
          #(is (nil? (parse-opts mock-invalid-args mock-option-specs)))))

      (testing "Successfully parses options when valid args are passed"
        (let [options (parse-opts mock-valid-args mock-option-specs)]
          (is (= (options mock-valid-args-key) mock-valid-args-value))))

      (testing "Options successfully read from config file."
        (with-redefs-fn {#'clojure.core/slurp (fn [_] mock-yaml-string)}
          #(let [args (into mock-valid-args ["--config-file" "foo.yaml"])
                options (parse-opts args mock-option-specs)]
            (is (= mock-yaml-arg-value (options mock-yaml-arg-key))))))

      (testing "Type based parsing works with CLI and config file input."
        (with-redefs-fn {#'clojure.core/slurp (fn [_] mock-yaml-file)}
          #(let [args-cli ["-i" "10" "-f" "1.5" "-l" "1,2,3"]
                 args-conf-file ["--config-file" "myconf.yaml"]
                 specs [["-i" "--int X" nil
                           :parse-fn (fn [i] (Integer/parseInt i))]
                          ["-f" "--float X" nil
                           :parse-fn (fn [f] (Float/parseFloat f))]
                          ["-l" "--list X" nil
                           :parse-fn (fn [s] (map (fn [i] (Integer/parseInt i))
                                                  (str/split s #",")))]]
                  cli-options (parse-opts args-cli specs)
                  file-options (parse-opts args-conf-file specs)]
            (is (= (:int cli-options) (:int file-options)))
            (is (= (:float cli-options) (:float file-options)))
            (is (= (:list cli-options) (:list file-options))))))))

(deftest test-simple-spec
  (let [int-parse-fn (second (sstypemap :int))
        vector-parse-fn (second (sstypemap :vector))
        not-blank? #(not (clojure.string/blank? %))]

    (testing "Parses simple spec into normal spec"
      (is (= (simple-spec [[:port :int "the port to serve on"]
                           ["hosts" :vector "hosts to connect to"]
                           [:name "string" :validate-fn not-blank?]
                           [:debug :boolean]])
             [[nil "--port INT" "the port to serve on"
               :parse-fn int-parse-fn]
              [nil "--hosts STRING1,STRING2,STRING3" "hosts to connect to"
               :parse-fn vector-parse-fn]
              [nil "--name STRING" ""
               :parse-fn nil
               :validate-fn not-blank?]
              [nil "--debug" "" :parse-fn nil]])))

    (testing "Simple specs work with parse-opts"
      (is (= (parse-opts ["--port=10" "--hosts=foo,bar,baz" "--name=Devon"]
                         (simple-spec [[:port :int "the port to serve on"]
                                       ["hosts" :vector "hosts to connect to"]
                                       [:name "string" :validate-fn not-blank?]]))
             {:port 10 :hosts ["foo" "bar" "baz"] :name "Devon"})))

    (testing "Boolean flag"
      (is (= (parse-opts [] (simple-spec [[:debug :boolean]]))
             {}))
      (is (= (parse-opts ["--debug"] (simple-spec [[:debug :boolean]]))
             {:debug true})))))

(defn reset-globals! []
  (reset! options {})
  (reset! global-specs []))

(deftest test-global-specs
  (let [opt-specs1 (simple-spec [[:port :int :default 8000]
                                 [:host :string :default "localhost"]
                                 [:prefix :string]])
        opt-specs2 (simple-spec [[:num-threads :int :default 10]
                                 [:log-file :string]])]
    (testing "add-option-specs sets defaults"
      (reset-globals!)
      (add-option-specs! opt-specs1)
      (is (= @options {:port 8000, :host "localhost"})))
    (testing "accept added option specs"
      (reset-globals!)
      (add-option-specs! opt-specs1)
      (set-options! ["--prefix" "cb"
                     "--port" "8080"
                     "--log-file" "/var/log/opts.log"]
                    opt-specs2)
      (is (= @options {:port 8080
                       :host "localhost"
                       :num-threads 10
                       :log-file "/var/log/opts.log"
                       :prefix "cb"}))))
  (reset-globals!))
