(ns com.keminglabs.cljs-react-perf.electron-main
  (:require-macros [com.keminglabs.cljs-react-perf.macros :refer [p pp timeout]]))

(enable-console-print!)

(def n
  "Number of times to repeat each benchmark"
  100)

(def ^js/electron electron
  (js/require "electron"))
(def app (.-app electron))
(def BrowserWindow (.-BrowserWindow electron))
(def ipc (.-ipcMain electron))

;;Allow us to run v8 garbage collector from JavaScript via `gc()`.
(.appendSwitch (.-commandLine app) "js-flags" "--expose-gc")

;;Don't use OS X dock icon, since it can steal focus away from our diligent hero and their terminal.
(def dock (.-dock app))
(when (exists? dock)
  (.hide dock))

;;Don't close electron when all windows are closed.
;;Must subscribe to this event to prevent Electron default behavior, which is to quit the app
(.on app "window-all-closed" (fn []))


(defn start-benchmark! [[component-name optimization-mode] cb]
  (let [^js/BrowserWindow w (BrowserWindow. (clj->js {:show false}))
        !measurements (atom [])]

    (letfn [(finish []
              (.close w)
              (.removeListener ipc "ready" on-render-ready)
              (.removeListener ipc "measurement" on-measurement)
              (cb @!measurements))

            (on-render-ready [e]
              (if (= n (count @!measurements))
                (finish)
                ;;otherwise, ask for another set of measurements
                (set! (.-returnValue e) component-name)))

            (on-measurement [e raw-measurement]
              (swap! !measurements conj (js->clj raw-measurement :keywordize-keys true))
              (set! (.-returnValue e) nil))]

      (.loadURL w (str "file://" js/__dirname "/../" optimization-mode ".html"))
      (.on ipc "ready" on-render-ready)
      (.on ipc "measurement" on-measurement))))


(defn next-benchmark!
  [benchmarks]
  (let [[x & xs] benchmarks]
    (if x
      (start-benchmark! x (fn [measurements]
                            ;;emit results, run next measurement
                            (let [[component-name optimization-mode] x]
                              (prn {:name component-name
                                    :optimization-mode optimization-mode
                                    :measurements measurements}))
                            (next-benchmark! xs)))
      (.quit app))))


(.on app "ready"
     (fn []

       (let [component-names (-> (drop 2 (.-argv js/process))
                                 seq
                                 (or ["app-1" "app-2" "app-3" "app-4" "app-5" "app-6" "app-7" "app-8" "app-9" "app-10" "app-11" "app-12" "app-13" "app-14" "app-15" "app-17"]))]


         (->> component-names
              (mapcat (fn [component-name]
                        (map vector
                             (repeat component-name)
                             ;;These should correspond to the names of the html files
                             ["simple"
                              "simple2"
                              "advanced"])))
              next-benchmark!))



       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
       ;;Uncomment this if you need to do some debuggin'

       ;; (do
       ;;   (when (exists? dock)
       ;;     (.show dock))
       ;;   (let [^js/BrowserWindow w (BrowserWindow. (clj->js {:show true}))]
       ;;     (.openDevTools (.-webContents w))
       ;;     (.loadURL w (str "file://" js/__dirname "/../simple.html#app-1"))))

       ))
