(require '[cljs.build.api :as cljs])
(import 'java.io.File)

;;java -cp `lein classpath` clojure.main dev_build.clj

(.start (Thread. (fn []
                   (cljs/watch (cljs/inputs "src/")
                               {:main 'com.keminglabs.cljs-react-perf.electron-main
                                :output-to "app/js/electron_main.js"
                                :output-dir "resources/out"
                                ;;This has to be optimizations :simple. :none and :whitespace don't work w/ Electron because of some relative path stuff: http://dev.clojure.org/jira/browse/CLJS-1444
                                :optimizations :simple}))))


(.start (Thread. (fn []
                   (cljs/watch (cljs/inputs "src/")
                               {:main 'com.keminglabs.cljs-react-perf.main
                                :output-to "app/js/simple.js"
                                :output-dir "resources/out"
                                ;; :infer-externs true
                                ;; :optimizations :advanced
                                }))))
