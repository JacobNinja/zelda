(ns zelda.window
  (:require [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def canvas (.getElementById js/document "world"))
(def context (.getContext canvas "2d"))
(def hit-points (.getElementById js/document "hit-points"))

(def border-color "#cdcdcd")
(def empty-color "#eee")
(def player-color "#000000")
(def obstacle-color "#cc0000")
(def enemy-color "0000ff")
(def flash-color "#fff000")

(def height 11)
(def width 16)
(def cell-size 
  (- (/ (- (.-innerHeight js/window) (.-offsetHeight hit-points)) height) 2))
(def sword-size (/ cell-size 6))

(defn- fill-square [[x y] color]
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
  (doseq [coord coords]
    (fill-square coord color)))

(defn- fill-empty []
  (doseq [x (range width)
          y (range height)]
    (fill-square [x y] empty-color)))

(defn- draw-strike [coords direction]
  (let [offset (- (/ cell-size 2) (/ sword-size 2))
        [window-x window-y] (map #(* cell-size %) coords)]
    (set! (.-fillStyle context) player-color)
    (if (#{:left :right} direction)
      (.fillRect context
                 window-x
                 (+ window-y offset)
                 cell-size
                 sword-size)
      (.fillRect context
                 (+ window-x offset)
                 window-y
                 sword-size
                 cell-size))))

(defn- heart-image []
  "<img src='images/heart.png' />")

(defn- fill-hp-meter [hp]
  (set! (.-innerHTML hit-points)
        (apply str (repeatedly hp heart-image))))

(defn- init-window []
  (set! (.-width canvas) (* cell-size width))
  (set! (.-height canvas) (* cell-size height))
  (fill-empty))  

(defn- draw-loop [draw]
  (go
   (loop []
     (let [{:keys [flash strike player obstacles enemies enemy-flash]} (<! draw)]
       (fill-empty)
       (fill-square (.-coord player) player-color)
       (fill obstacles obstacle-color)
       (fill enemies enemy-color)
       (fill-hp-meter (.-hp player))
       (when flash
         (fill-square flash flash-color))
       (when enemy-flash
         (fill enemy-flash flash-color))
       (when strike
         (draw-strike strike (.-direction player))))
     (recur))))

(defn init [draw]
  (init-window)
  (draw-loop draw)
  {:dimensions [width height]})
