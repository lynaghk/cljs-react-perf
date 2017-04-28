(require '[cljs.build.api :as cljs])
(import 'java.io.File)

;;java -cp `lein classpath` clojure.main build.clj

(prn "Building electron")
(cljs/build (cljs/inputs "src/")
            {:main 'com.keminglabs.cljs-react-perf.electron-main
             :output-to "app/js/electron_main.js"
             :output-dir "resources/out"
             ;;This has to be optimizations :simple. :none and :whitespace don't work w/ Electron because of some relative path stuff: http://dev.clojure.org/jira/browse/CLJS-1444
             :optimizations :simple})
(prn "Done.")



(doseq [config [{:main 'com.keminglabs.cljs-react-perf.main
                 :output-to "app/js/simple.js"
                 :optimizations :simple}

                {:main 'com.keminglabs.cljs-react-perf.main
                 :output-to "app/js/simple2.js"
                 :optimizations :simple
                 :static-fns true
                 :optimize-constants true}

                {:main 'com.keminglabs.cljs-react-perf.main
                 :output-to "app/js/advanced.js"
                 :optimizations :advanced
                 :infer-externs true}]]

  (prn config)
  (cljs/build (cljs/inputs "src/") config)
  (prn "Done."))
