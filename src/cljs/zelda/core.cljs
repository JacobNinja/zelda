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
(def opposite-direction {:left :right
                         :right :left
                         :up :down
                         :down :up})

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
  (let [next-player (.tick (env :player) (env :key))]
    (if (and (in-bounds? (env :dimensions) (.-coord next-player))
             (not (collides-with-obstacles? (env :obstacles) (.-coord next-player))))
      (assoc env :player next-player)
      env)))

(defn- swing-sword [env]
  (if (= (env :key) :space)
    (assoc env :swing (adjust-position (.-coord (env :player)) (.-direction (env :player))))
    (dissoc env :swing)))

(defn- adjust-key [env keyboard-check]
  (assoc env :key keyboard-check))

(defn- player-collision-check [env]
  (if-let [player-collisions ((set (env :enemies)) (.-coord (env :player)))]
    (let [next-player (-> (env :player)
                           .tickBackwards
                           (.hit 1))]
      (merge env {:player next-player
                  :flash (.-coord next-player)}))
    (dissoc env :flash)))

(defn- game-over [env]
  (if (zero? (.-hp (env :player)))
    (assoc env :game-over "You died!")
    env))

(deftype Player [coord hp direction]
  Object
  (tick [this new-direction]
    (if-let [new-coord (adjust-position coord new-direction)]
      (Player. new-coord hp new-direction)
      this))
  (tickBackwards [this]
    (.tick this (opposite-direction direction)))
  (hit [this amount]
    (Player. coord (- hp amount) direction)))

(defn- init-env [env]
  (merge env 
         {:player (Player. [5 5] 3 :right)
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
                        player-collision-check
                        game-over)]
       (if-not (env :game-over)
         (recur (or (<! (timeout 200)) next-env))
         (js/alert (str "Game over!" \newline (env :game-over))))))))

(defn ^:export init []
  (let [draw-chan (chan)
        env (zelda.window/init draw-chan)]
    (keyboard-loop)
    (game-loop draw-chan env)))

