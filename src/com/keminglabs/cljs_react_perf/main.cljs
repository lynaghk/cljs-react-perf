(ns com.keminglabs.cljs-react-perf.main
  (:require-macros [com.keminglabs.cljs-react-perf.macros :refer [p pp timeout]])
  (:require [taoensso.tufte :as tufte]
            [rum.core :as rum]))


(def n
  "The number of times to render the requested component before returning measurements."
  100)

;;export ELECTRON_ENABLE_LOGGING=true
(enable-console-print!)

(def ipc
  (aget (js/require "electron") "ipcRenderer"))

(def ReactPerf
  (js/require "react-addons-perf"))

(def Process
  (js/require "process"))


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 1

(rum/defc item-1
  [x]
  [:.item x])

(rum/defc list-1
  [items]
  [:.list
   (map item-1 items)])

(rum/defc app-1
  [state]
  [:.app
   (list-1 (:items state))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 2

(defn item-2
  [x]
  [:.item x])

(rum/defc list-2
  [items]
  [:.list
   (map item-2 items)])

(rum/defc app-2
  [state]
  [:.app
   (list-2 (:items state))])



;;;;;;;;;;;;;;;;;;;;;
;;Scenario 3

(rum/defc item-3
  [x]
  [:.item {:on-click (fn [] (p (str x " was clicked!")))}
   x])

(rum/defc list-3
  [items]
  [:.list
   (map item-3 items)])

(rum/defc app-3
  [state]
  [:.app
   (list-3 (:items state))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 4

(defn handle-click-4
  [e]
  (p (str (aget e "dataset" "itemId") " was clicked!")))

(rum/defc item-4
  [x]
  [:.item {:data-item-id x
           :on-click handle-click-4}
   x])

(rum/defc list-4
  [items]
  [:.list
   (map item-4 items)])

(rum/defc app-4
  [state]
  [:.app
   (list-4 (:items state))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 5

(rum/defc item-5
  [x]
  [:.item {:data-item-id x}
   x])

(rum/defc list-5
  [items]
  [:.list {:on-click (fn [e]
                       (p (str (aget e "target" "dataset" "itemId") " was clicked!")))}
   (map item-5 items)])

(rum/defc app-5
  [state]
  [:.app
   (list-5 (:items state))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 6

(rum/defc item-6 < {:key-fn (fn [x] (str x))}
  [x]
  [:.item x])

(rum/defc list-6
  [items]
  [:.list
   (map item-6 items)])

(rum/defc app-6
  [state]
  [:.app
   (list-6 (:items state))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 7

(rum/defc app-7
  [state]
  [:.app
   (list-1 (shuffle (:items state)))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 8

(rum/defc app-8
  [state]
  [:.app
   (list-6 (shuffle (:items state)))])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 9

(rum/defc item-9
  [x]
  [:.item
   (when-not (zero? x)
     (item-9 (dec x)))])

(rum/defc app-9
  [state]
  [:.app
   (item-9 400)])


;;;;;;;;;;;;;;;;;;;;;
;;Scenario 10

(rum/defc item-10
  [x]
  [:.item x])

(rum/defc app-10
  [state]
  [:.app
   (loop [n 400 res (item-10 nil)]
     (if (zero? n)
       res
       (recur (dec n) (item-10 res))))])









;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;Shared infrastructure

(def !state
  (atom {:items (vec (range 1000))}))


(defn render!
  [component]
  (tufte/p :react-render
    (.render js/ReactDOM (component @!state) (.getElementById js/document "container"))))


(defn profile
  [component]
  (let [[_ stats] (tufte/profiled {}
                                  (.start ReactPerf)
                                  (render! component)
                                  (.stop ReactPerf))]

    {;;memory usage in kB
     :private-memory (aget (.getProcessMemoryInfo Process) "privateBytes")

     :react-wasted (for [d (.getWasted ReactPerf)]
                     {:id (aget d "key")
                      :duration (aget d "inclusiveRenderDuration")
                      :instance-count (aget d "instanceCount")
                      :render-count (aget d "renderCount")})

     :tufte (for [[id d] (:id-stats-map stats)]
              ;;convert timing from nanoseconds to milliseconds
              {:id id :duration (* 1e-6 (:time d))})}))




;;Give the page a second for things to settle down or whatevs.
(timeout 1000
         (let [component (case (aget js/window "location" "hash")
                           "#app-1" app-1
                           "#app-2" app-2
                           "#app-3" app-3
                           "#app-4" app-4
                           "#app-5" app-5
                           "#app-6" app-6
                           "#app-7" app-7
                           "#app-8" app-8
                           "#app-9" app-9
                           "#app-10" app-10)

               measurements (mapv profile (repeat n component))]

           (.send ipc "measurements" (clj->js measurements))))
