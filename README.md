# To-GLSL

## Introduction

To-GLSL is a library for converting Clojure s-expressions to GLSL.  

### Usage
`[to-glsl "0.1.0-SNAPSHOT"]`

### Example

```clojure
(def basic-vs
  '(do
     (def-attribute vec4 vertex-position)
     (def-attribute vec2 tex-coord-attr)

     (def-uniform mat4 model-view)
     (def-uniform mat4 projection)

     (def-varying (highp vec2) tex-coord)

     (defn main (void [])
       (set! tex-coord tex-coord-attr)
       (set!
         gl/position
         (* projection model-view vertex-position)))))

(def basic-fs
  '(do
     (def-uniform sampler-2D sampler)
     (def-varying (highp vec2) tex-coord)

     (defn main (void [])
       (set! gl/frag-color (texture-2D sampler tex-coord)))))

(require '[to-glsl.core :refer [->glsl]])

(def vs-source (->glsl basic-vs))
(def fs-source (->glsl basic-fs))
```

### Supported Language
`defn`
```clojure
(defn add-one (void [(int x)]) (+ x 1))
```
becomes:
```glsl
void addOne(int x) {
  x + 1;
}
```
Note: types must be specified

`def`
```clojure
(def float pi 3.14159)
```
becomes
```glsl
float pi = 3.14159;
```

`def-varying` - like def but creates a varying 

`def-unifrom` - like def but creates a uniform

`def-attribute` - like def but creates an attribute

`set!`
```clojure
(set! x 5)
```
becomes
```glsl
x = 5;
```

`when`
```clojure
(when (and (= x 10) (= z 30))
  (set! y 20))
```
becomes
```glsl
if (x == 10 && z == 30) {
  y = 20;
}

```

`if`
```clojure
(set! y (if (= x 10) 20 10))
```
becomes
```glsl
y = x == 10 ? 20 : 10;
```

`and`, `or`, `=`, `+`, `-`, `/`, `*`, `bit-and`, `bit-or`, `bit-xor`, `bit-not`, `bit-shift-right`, `bit-shift-left` - all compile into appriate infix form.

`inc` and `dec` - compile into `++` and `--` repectively.

`field`
```clojure
(field vec x)
```
becomes
```glsl
vec.x
```
`swizzle`
```clojure
(swizzle vec x z)
```
becomes
```glsl
vec.xz
```

All symbols are automatically converted from kebab case into camel case.

The GLSL `gl_` symbols like `gl_Position` and `gl_FragColor` can be accessed under the `gl/` namespace in kebab case.  i.e. `gl/frag-color`.

To-GLSL doesn't make any attempt to validate the provided code.

### License
Copyright 2019 Jeremy Kross

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
