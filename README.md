# cb.cljbeat.options
Command line option parsing utilities.
See `(doc cb.clbjeat.options/parse-opts)` for more details.

## Installation
With leiningen: `[com.chartbeat.cljbeat/options "1.0.0"]`

## Example Usage

#### parse-opts
```clojure
(ns my-app
  (:require [cb.cljbeat.options :refer [parse-opts]]))

(def option-specs [["-c" "--count NUMBER" "Lines to count."
                    :default 10
                    :parse-fn #(Integer/parseInt %)]
				           [nil "--hosts" "Lists of hosts."
				            :parse-fn #(clojure.string/split % #",")]
                   ["-d" "--debug" "Optional boolean debug flag."]])
(def -main
  [& args]
  (let [options (parse-opts args option-specs)]
    (if (nil? options)
      (System/exit 1))
    (if (options :debug)
      (println "using debugging"))))
```

In addition to passing all arguments in the command line, you can pass a yaml
file to the `--config-file` option. So if you have a yaml file like such:
```yaml
foo: 1
```
Then `(options :foo)` would return `1`. If you include the same arguments in
the command line in addition to the config file, they will override the value
in the file:

`lein run --config-file=conf.yaml --foo=2` -> `(options :foo)` will return 2.

The order of precedence is:

1. Values supplied by command line.
2. Values in yaml file.
3. Default values in option spec.

Right now this will only support yaml files that are flat lists (if you need to
pass a list as an argument just comma seperate the values like you would on the
command line).

#### set-options! and the global options map

You can choose to use the globally defined `options` map to avoid passing
options all the way down the call stack.

```clojure
(ns my-app
  (:require [cb.cljbeat.options :refer [parse-opts options]]
            [my-lib :as lib]))

(def option-specs [["-d" "--debug" "Optional boolean debug flag."]])

(defn inner-fn
  []
  (if (@options :debug)
    (println "using debugging")))

(defn -main
  [& args]
  (set-options! args options-specs)
  (inner-fn))
```

#### add-option-specs!

Libraries can also add option specs that will bubble up to set-options!

```clojure
(ns my-lib
  (:require [cb.cljbeat.options :refer [add-option-specs! options]]))

(add-option-specs! [[nil "--my-lib-log-file"
                     "Log location for my-lib"
                     :default "/var/log/my_lib.log"]])

(defn foo
  []
  (log-to-file "foo" (@options :my-lib-log-file)))
```
All these options will show up with `--help`. To not display them, use
`--help-short`.


#### simple-spec
The `simple-spec` function allows for simple option specs with common types. The
types supported are
* strings
* ints
* longs
* floats
* vectors (of strings)

Based on the type argument passed, a matching parse-fn function is created.
```clojure
(ns my-app
  (:require [cb.cljbeat.options :refer [parse-opts simple-spec]]))

(def options-spec
  (simple-spec [[:count :int "Lines to count."]
		[:hosts :vector "list of hosts."]
                [:debug :boolean]]))

(def -main
  [& args]
  (let [options (parse-opts args option-specs)
        config (slurp (options :config-file))
        count (options :count)]
    (if (options :debug)
      (println "using debugging"))))
```

See `(doc cb.cljbeat.options/simple-spec)` for more details.
