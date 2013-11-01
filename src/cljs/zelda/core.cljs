(ns zelda.core
  (:require [cljs.core.async :as async 
             :refer [<! >! chan timeout alts!]]
            [clojure.browser.event :as event]
            [goog.events.KeyHandler]
            [goog.events.KeyCodes :as key-codes]
            [zelda.window])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def keyboard (goog.events.KeyHandler. js/document))
(def keyboard-chan (chan (async/sliding-buffer 1)))
(def valid-keys {key-codes/LEFT :left
                 key-codes/RIGHT :right
                 key-codes/UP :up
                 key-codes/DOWN :down
                 key-codes/SPACE :space})

(defn- keyboard-loop []
  (event/listen keyboard 
                "key"
                (fn [k]
                  (when-let [key (valid-keys (.-keyCode k))]
                    (go (>! keyboard-chan key))))))

(defn- in-bounds? [[width height] [x y]]
  (and (>= x 0) (>= y 0) (< x width) (< y height)))

(defn- collides-with-obstacles? [obstacles coord]
  (some #(= % coord) obstacles))

(defn- adjust-position [[x y] direction]
  (condp = direction
    :right [(inc x) y]
    :left [(dec x) y]
    :up [x (dec y)]
    :down [x (inc y)]
    nil))

(defn- adjust-player [env]
  (let [next-coord (adjust-position (env :player) (env :key))]
    (if (and next-coord
             (in-bounds? (env :dimensions) next-coord)
             (not (collides-with-obstacles? (env :obstacles) next-coord)))
      (merge env {:player next-coord
                  :direction (env :key)})
      env)))

(defn- swing-sword [env]
  (if (= (env :key) :space)
    (assoc env :swing (adjust-position (env :player) (env :direction)))
    (dissoc env :swing)))

(defn- adjust-key [env keyboard-check]
  (assoc env :key keyboard-check))

(defn- player-collision-check [env]
  (if-let [player-collisions ((set (env :enemies)) (env :player))]
    (assoc env :hp (dec (env :hp)))
    env))

(defn- init-env [env]
  (merge env 
         {:player [5 5]
          :hp 3
          :obstacles [[8 8]]
          :enemies [[10 10]]}))

(defn- game-loop [draw initial-env]
  (go
   (loop [env (init-env initial-env)]
     (>! draw env)
     (let [[keyboard-check _] (alts! [keyboard-chan (timeout 1)])
           next-env (-> env
                        (adjust-key keyboard-check)
                        adjust-player
                        swing-sword
                        player-collision-check)]
       (<! (timeout 200))
       (recur next-env)))))

(defn ^:export init []
  (let [draw-chan (chan)
        env (zelda.window/init draw-chan)]
    (keyboard-loop)
    (game-loop draw-chan env)))

