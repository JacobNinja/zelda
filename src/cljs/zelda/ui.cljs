(ns zelda.ui
  (:require [zelda.window]
            [cljs.core.async :refer [>! <! chan]]
            [goog.events.KeyHandler]
            [goog.events.KeyCodes :as key-codes]
            [clojure.set :refer [difference]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def keyboard-chan (chan))
(def draw-chan (chan))

(def event (atom :obstacle))

(def keyboard (goog.events.KeyHandler. js/document))

(defn- push-mouse [e]
  (go (>! draw-chan [(.-pageX e) (.-pageY e)])))

(defn- mousedown [e]
  (set! (.-onmousemove zelda.window/canvas) #(push-mouse %)))

(defn- mousemove [e]
  (push-mouse e))

(defn- react-to-key [e]
  (let [key (.-keyCode e)]
    (when (= key key-codes/SPACE)
      (if (= @event :obstacle)
        (reset! event :remove)
        (reset! event :obstacle)))))

(defn- draw-loop []
  (go
   (loop [obstacles #{}]
     (let [window-coords (<! draw-chan)
           coords (map #(int (/ % @zelda.window/cell-size)) window-coords)
           next-obstacles (condp = @event
                            :obstacle (conj obstacles coords)
                            :remove (difference obstacles #{coords})
                            obstacles)]
       (zelda.window/fill-empty)
       (zelda.window/fill next-obstacles zelda.window/obstacle-color)
       (.log js/console (.toString next-obstacles))
       (recur next-obstacles)))))

(defn ^:export init []
  (zelda.window/init-window)
  (let [canvas zelda.window/canvas]
    (set! (.-onmousedown canvas) mousedown)
    (set! (.-onmouseup canvas) (fn [] (set! (.-onmousemove canvas) nil)))
    (set! (.-onkeydown canvas) react-to-key)
    (.setAttribute canvas "tabindex" 0) ; WTF CANVAS
    (draw-loop)))
