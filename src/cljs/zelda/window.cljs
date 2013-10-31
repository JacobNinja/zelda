(ns zelda.window
  (:require [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def canvas (.getElementById js/document "world"))
(def context (.getContext canvas "2d"))

(def border-color "#cdcdcd")
(def empty-color "#eee")
(def player-color "#000000")
(def obstacle-color "#cc0000")

(def cell-size 60)
(def height (int (/ (.-innerHeight js/window) cell-size)))
(def width (int (/ (.-innerWidth js/window) cell-size)))

(defn- fill-square [x y color]
  (set! (.-fillStyle context) color)
  (set! (.-strokeStyle context) border-color)
  (.fillRect context
             (* x cell-size)
             (* y cell-size)
             cell-size
             cell-size)
  (.strokeRect context
               (* x cell-size)
               (* y cell-size)
               cell-size
               cell-size))

(defn- fill [coords color]
  (doseq [[x y] coords]
    (fill-square x y color)))

(defn- fill-empty []
  (doseq [x (range width)
          y (range height)]
    (fill-square x y empty-color)))

(defn- init-window []
  (set! (.-width canvas) (* cell-size width))
  (set! (.-height canvas) (* cell-size height))
  (fill-empty))  

(defn- draw-loop [draw]
  (go
   (loop []
     (let [env (<! draw)]
       (fill-empty)
       (fill [(env :player)] player-color)
       (fill (env :obstacles) obstacle-color))
     (recur))))

(defn init [draw]
  (init-window)
  (draw-loop draw))
