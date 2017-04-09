(require '[cljs.build.api :as cljs])
(import 'java.io.File)

;;java -cp `lein classpath` clojure.main build.clj

(def cljs-config
  {:main 'com.keminglabs.cljs-react-perf.main
   :output-to "app/js/main.js"
   :output-dir "resources/out"
   :language-in :ecmascript5
   :optimizations :simple})


;;Note: cljs compiler seems to silently ignore a `:main` key unless optimizations are simple or advanced. Potentially could be fixed by adding :whitespace to clojurescript/src/main/clojure/cljs/closure.clj:2106.
(.start (Thread. (fn []
                   (prn "Building electron")
                   (cljs/watch (cljs/inputs "src/")
                               {:main 'com.keminglabs.cljs-react-perf.electron-main
                                :output-to "app/js/electron_main.js"
                                :output-dir "resources/out"
                                :language-in :ecmascript5
                                ;;This has to be optimizations :simple. :none and :whitespace don't work w/ Electron because of some relative path stuff: http://dev.clojure.org/jira/browse/CLJS-1444
                                :optimizations :simple}))))

(.start (Thread. (fn []
                   (prn "Building UI with simple optimizations")
                   (cljs/watch (cljs/inputs "src/")
                               cljs-config))))



;; (prn "Building UI with advanced optimizations")
;; (cljs/build (cljs/inputs "src/")
;;             (merge cljs-config {:optimizations :advanced
;;                                 :infer-externs true
;;                                 :output-to "app/js/main_advanced.js"}))
