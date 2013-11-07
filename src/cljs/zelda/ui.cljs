(ns zelda.ui
  (:require [zelda.window]
            [cljs.core.async :refer [>! <! chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def mouse-chan (chan))

(defn- push-mouse [e]
  (go (>! mouse-chan [(.-pageX e) (.-pageY e)])))

(defn- mousedown [e]
  (set! (.-onmousemove zelda.window/canvas) #(push-mouse %)))

(defn- mousemove [e]
  (push-mouse e))

(defn- mouse-loop []
  (go
   (loop [obstacles #{}]
     (let [window-coords (<! mouse-chan)
           coords (map #(int (/ % @zelda.window/cell-size)) window-coords)
           next-obstacles (conj obstacles coords)]
       (zelda.window/fill-square coords zelda.window/obstacle-color)
       (.log js/console (.toString next-obstacles))
       (recur next-obstacles)))))


(defn ^:export init []
  (zelda.window/init-window)
  (let [canvas zelda.window/canvas]
    (set! (.-onmousedown canvas) mousedown)
    (set! (.-onmouseup canvas) (fn [] (set! (.-onmousemove canvas) nil)))
    (mouse-loop)))
