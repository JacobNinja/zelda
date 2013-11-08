(ns zelda.window
  (:require [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def canvas (.getElementById js/document "world"))
(def context (.getContext canvas "2d"))
(def hit-points (.getElementById js/document "hit-points"))
(def inventory-html (.getElementById js/document "inventory"))

(def border-color "#cdcdcd")
(def empty-color "#eee")
(def player-color "#00cc00")
(def obstacle-color "#cc0000")
(def enemy-color "0000ff")
(def flash-color "#fff000")

(def height 11)
(def width 16)
(def cell-size (atom nil))

(def images {:heart "heart.png"
             :sword "sword.png"})
(defn- image-for [item]
  (str "<img src='images/" (images item) "' />"))

(defn- fill-square [[x y] color]
  (set! (.-fillStyle context) color)
  (set! (.-strokeStyle context) border-color)
  (.fillRect context
             (* x @cell-size)
             (* y @cell-size)
             @cell-size
             @cell-size)
  (.strokeRect context
               (* x @cell-size)
               (* y @cell-size)
               @cell-size
               @cell-size))

(defn- fill [coords color]
  (doseq [coord coords]
    (fill-square coord color)))

(defn- fill-empty []
  (doseq [x (range width)
          y (range height)]
    (fill-square [x y] empty-color)))

(defn- draw-strike [coords direction]
  (let [sword-size (/ @cell-size 6)
        offset (- (/ @cell-size 2) (/ sword-size 2))
        [window-x window-y] (map #(* @cell-size %) coords)]
    (set! (.-fillStyle context) player-color)
    (if (#{:left :right} direction)
      (.fillRect context
                 window-x
                 (+ window-y offset)
                 @cell-size
                 sword-size)
      (.fillRect context
                 (+ window-x offset)
                 window-y
                 sword-size
                 @cell-size))))

(defn- fill-hp-meter [hp]
  (set! (.-innerHTML hit-points)
        (apply str (repeatedly hp #(image-for :heart)))))

(defn- fill-inventory [inventory]
  (set! (.-innerHTML inventory-html)
        (apply str (map #(image-for %) inventory))))

(defn- fill-pickup-items [items]
  (doseq [[item [x y]] items]
    (let [image (js/Image.)
          offset (/ @cell-size 4)
          half (/ @cell-size 2)
          window-x (+ offset (* x @cell-size))
          window-y (+ offset (* y @cell-size))]
      (set! (.-onload image)
            #(.drawImage context image window-x window-y half half))
      (set! (.-src image) (str "images/" (images item))))))

(defn- init-window []
  (reset! cell-size (- (/ (.-innerHeight js/window) height) 2))
  (set! (.-width canvas) (* @cell-size width))
  (set! (.-height canvas) (* @cell-size height))
  (fill-empty))

(defn- draw-loop [draw]
  (go
   (loop []
     (let [{:keys [flash strike player obstacles enemies enemy-flash
                   inventory pickup-items]} (<! draw)]
       (fill-empty)
       (fill-square (.-coord player) player-color)
       (fill obstacles obstacle-color)
       (fill enemies enemy-color)
       (fill-hp-meter (.-hp player))
       (fill-inventory inventory)
       (fill-pickup-items pickup-items)
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
