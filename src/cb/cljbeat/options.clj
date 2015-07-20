(ns cb.cljbeat.options
  (:require [clojure.string :refer [join]]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clj-yaml.core :as yaml]
            [clojure.string :as str])
  (:gen-class))

(def required-arguments #'clojure.tools.cli/required-arguments)
(def tokenize-args #'clojure.tools.cli/tokenize-args)
(def parse-option-tokens #'clojure.tools.cli/parse-option-tokens)
(def summarize #'clojure.tools.cli/summarize)

(def fail-message "Improper usage of options. See --help for correct usage.")
(def help-option ["-h" "--help" "Print help."])
(def file-option [nil "--config-file PATH" "Path to options yaml file."])

(defn wrap-parse-fn
  "Wraps the parse-fn function so that it will ignore any non string inputs.
  This is so parsing will only be done on inputs from the command line."
  [spec]
  (assoc spec :parse-fn #(if (and (string? %) (spec :parse-fn))
                           ((spec :parse-fn) %) %)))

(defn- compile-option-specs
  [specs]
  (let [specs (into specs [file-option help-option])
        compiled-specs (#'clojure.tools.cli/compile-option-specs specs)]
    (map wrap-parse-fn compiled-specs)))

(defn- print-failure [errors]
  (let [indented-errors (map #(str "  " %) errors)
        out (join "\n" (cons fail-message indented-errors))]
    (println out)))

(defn- log-failure [errors]
  (let [out (join " " (cons fail-message errors))]
    (println out)))

;;This is where set-options! will store parsed options.
(def options (atom {}))

;; This is where add-option-specs! will build up specs from libraries.
(def global-specs (atom []))

(def sstypemap
  {:vector  ["STRING1,STRING2,STRING3" #(clojure.string/split % #",")]
   :string  ["STRING" nil]
   :long    ["LONG" #(Long/parseLong %)]
   :int     ["INT" #(Integer/parseInt %)]
   :float   ["FLOAT" #(Float/parseFloat %)]
   :boolean ["" nil]})

(defn simple-spec [spec]
  "Create a parse-opts compatible spec using vectors of string, strings, longs,
  ints, and float arguments.
  example:
    (simple-spec [[:port :int \"the port to serve on\"]
                  [\"hosts\" :vector \"hosts to connect to\"]
                  [:name \"string\" :validate-fn #(not (blank? %))]
                  [:debug :boolean \"debug flag\"])

    => [[nil \"--port INT\" \"the port to serve on\"
         :parse-fn #(Integer/parseInt %)]
        [nil \"--hosts STRING1,STRING2,STRING3\" \"hosts to connect to\"
         :parse-fn #(clojure.string/split % #\",\")]
        [nil \"--name STRING\" \"\"
         :parse-fn nil
         :validate-fn #(not (clojure.string/blank? %))]
        [nil \"--debug\" :parse-fn nil]]

  The first two, required, entries to each argument spec are the name and type.
  They can be a keyword or a string. The name denotes the name of the argument
  and the type translates to a parse-fn function.

  The optional third argument is a description. Any additional arguments are
  added as optional arguments to the normal spec created. An additional parse-fn
  argument will ultimately override the one created by the type."
  (map (fn [[arg-name arg-type & rest_]]
         (let [arg-name (name arg-name)
               [hint parse-fn] (get sstypemap (keyword arg-type) ["???" nil])
               [desc opts] (if (string? (first rest_))
                             [(first rest_) (rest rest_)]
                             ["" rest_])
               long-name (clojure.string/trim (str "--" arg-name " " hint))]
           (into [nil long-name desc :parse-fn parse-fn]
                 opts)))
       spec))

(defn parse-opts
  "cb.cljbeat.options/parse-opts is a wrapper around clojure.tools.cli/parse-opts
  which adds error handling and summary printing with a -h or --help flag that's
  automatically appended to the options-specs.

  The API of this function is near identical to the clojure.tools.cli equivilent
  and can be further understood by reading (doc clojure.tools.cli/parse-opts).
  Unlike clojure.tools.cli/parse-opts however, this function returns the options
  map directly, or nil if parsing failed.

  example usage:
  (ns my-app
    (:require [cb.cljbeat.options :refer [parse-opts]]))

  (def option-specs [[\"-c\" \"--count NUMBER\" \"Lines to count.\"
                      :default 10
                      :parse-fn #(Integer/parseInt %)]
                     [\"-d\" \"--debug\" \"Optional boolean debug flag.\"]])
  (def -main
    [& args]
    (let [options (parse-opts option-specs))
          count (options :count)]
      (if (options :debug)
        (println \"using debugging\"))))

  In addition to passing all arguments in the command line, you can pass a yaml
  file to the :config-file option. So if you have a yaml file like such:

    foo: 1

  Then (options :foo) would return one. If you include the same arguments in
  the command line in addition to the config file, they will override the value
  in the file:

  lein run --config-file=conf.yaml --foo=2 -> (options :foo) will return 2.
  "
  [args option-specs]
  (let [specs (compile-option-specs option-specs)
        req (required-arguments specs)
        [tokens rest-args] (tokenize-args req args)
        from-file-path (last (first
                         (filter #(= (second %) "--config-file") tokens)))
        from-file (if from-file-path
                    (yaml/parse-string (slurp from-file-path))
                    {})
        file-tokens (map (fn [[k v]] [:long-opt (str "--" (name k)) v])
                         from-file)
        [opts errors] (parse-option-tokens specs (concat file-tokens tokens))]
    (cond
      (opts :help) (println (summarize specs))
      (opts :help-short) (println
                          (summarize
                           (filter #(not (.contains
                                          (map :id (compile-option-specs
                                                    @global-specs))
                                          (:id %)))
                                   specs)))
      (> (count errors) 0) (do
                             (print-failure errors)
                             (log-failure errors))
      :else opts)))

(defn add-option-specs!
  [specs]
  (swap! global-specs concat specs)
  ;; fill in the defaults in case set-options! is never called
  (swap! options merge (parse-opts "" specs)))

(defn set-options!
  "This function will run parse-opts and then store the result in the options
  var in this namespace. This new value will be accessible anywhere the options
  var is imported. Returns true if option parsing succeeded, false otherwise."
  [args option-specs]
  (let [parsed-opts
        (parse-opts args
                    (concat (simple-spec[[:help-short :boolean
                                          "Print help with only top level options."]])
                            option-specs
                            @global-specs))]
    (if (nil? parsed-opts)
      false
      (do (swap! options merge parsed-opts)
          true))))

