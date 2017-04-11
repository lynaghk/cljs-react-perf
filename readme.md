## Overview

Hi friends,

I'm a big fan of [Rum](https://github.com/tonsky/rum/) / [Sablono](https://github.com/r0man/sablono) for my ClojureScript DOM templating needs.
These libraries give me all the semantics I want, but I don't have much experience with performance tuning.
Most of the React performance tips I've found are (unsurprisingly) JavaScript-specific, so I thought it'd be nice to create a ClojureScript-specific resource.

My goal with this repo is to establish a set of tested idioms to help folks keep the DOM part of their UI fast.
Ideally, "fast" means re-renderable in under 16 ms, using a minimal amount of memory.
A few notes:

+ I'm interested only in re-renders, not the initial render. This may change later.
+ I'm interested only in JS timing. Once that becomes sufficiently fast, we can use the [Chrome Tracing API](https://www.chromium.org/developers/how-tos/trace-event-profiling-tool) to monitor browser layout, paint, etc.
+ I'm not trying to do anything fancy to warm up the JIT or whatever. If you have suggestions for how to warm up the JIT, tame the garbage collector, or otherwise prevent non-deterministic timing behavior, please open an issue.
+ The default behavior is to render 100 times, then report the mean timing with standard deviation. I've only counted to 100 once or twice in my entire life, so I think it's a pretty big number. If you can think of a bigger number, you can try it at the top of [main.cljs](src/com/keminglabs/cljs_react_perf/main.cljs).


## How to help

I'd love your help in exploring all of this complicated stuff!

I'm writing up high-level performance questions I have about ClojureScript / React as issues on this repository.
Feel free to open up issues with your own questions --- then we can come up with relevant tests together and close the issue when we figure out the answer!


## Running the benchmarks yourself

This project uses [Leiningen](https://leiningen.org/) for Clojure/ClojureScript dependency resolution and [Yarn](https://yarnpkg.com/en/) for Electron/JS dependency resolution.
I'm running everything on a 2013 Macbook Air 1.7 GHz i7 processor, 8GB RAM, OS X 10.9.5.

Run:

    yarn install
    
once to install Electron.
(I'm using Electron so that we can pin down an exact environment and automatically run each benchmark in its own process.)

Then run:

    java -cp `lein classpath` clojure.main build.clj
    
to start ClojureScript complier watchers.
Then run:

    export ELECTRON_ENABLE_LOGGING=true
    yarn start
    
to actually execute the perf tests.

In terms of structure, there are only two source files:

1. [electron_main.cljs](src/com/keminglabs/cljs_react_perf/electron_main.cljs) corresponds to the electron "main" process, which manages the headless browser windows that actually run the benchmarks. The actual measurement aggregation and printing code is run by this process.

1. [main.cljs](src/com/keminglabs/cljs_react_perf/main.cljs) corresponds to the electron "render" process, and defines the React components to be benchmarked, renders them 50 times each, and sends results to the benchmark runner.

I'm trying to keep all the infrastructure very minimal, so there's no error handling, core.async, Leiningen plugins, or anything fancy.

## How Sablono compiles hiccup to React elements

@r0man, the author of Sablono, explained to me that Sablono compiles hiccup markup in two ways:

1. **Macros**: If the markup is unambiguous (e.g., `[:div {}]`, which can only be interpreted as a div with no attributes and no children), Sablono will emit React elements at compile time via Clojure macros.

1. **Runtime interpretation**:  if the markup is ambiguous (e.g., `[:div (foo)]`, where it's not clear whether the function `foo` returns an attribute map or children elements), then Sablono emits calls to its runtime interpreter.

In theory, the macro approach should be faster than the runtime interpretation approach.

For an overview of this architecture, see @gigamonkey's *Practical Common Lisp* chapters on an HTML generation library consisting of [an interpreter](http://www.gigamonkeys.com/book/practical-an-html-generation-library-the-interpreter.html) and [compiler](http://www.gigamonkeys.com/book/practical-an-html-generation-library-the-compiler.html).


## On preventing runtime interpretation

In theory, Sablono should be faster when it knows more about your markup at compile time, and can emit React elements immediately rather than calls to the runtime interpretation machinery.
Scenarios 13 and 14 below test the `:attrs` metadata tag (see the [sablono compiler source](https://github.com/r0man/sablono/blob/fb5d756c4201598fe8737ae2877e76f9c25a96f1/src/sablono/compiler.clj#L150)).
Scenarios 15 and 16 test disambiguating attribute maps and children.


|  # | timing (ms) |                                                                    |
|----|-------------|--------------------------------------------------------------------|
| 13 | 56 ± 11     | Function invocation that returns attribute map, with no :attrs tag |
| 14 | 58 ± 11     | Function invocation that returns attribute map, with :attrs tag    |
| 15 | 5 ± 2       | Ambiguous attr/content                                             |
| 16 | 3 ± 2       | Unambiguous attr/content                                           |

Looks like the real result here is that you should avoid invoking a function to return your attribute map.


## On lists

In an app I'm currently working on, the slowest part is a list.
There are two ways I can think of structuring a list.

| # | timing (ms) |                                                                                                    |
|---|-------------|----------------------------------------------------------------------------------------------------|
| 1 | 7 ± 3       | The list is a component, which creates another component for each list item.                       |
| 2 | 112 ± 21    | The list is a component, which invokes a function that returns a hiccup vector for each list item. |


The advantage of #1 is that we can optimize re-renders with `:should-update`, but it's not obvious if/when that becomes faster than creating and diffing plain vectors.
In the re-render timings above, though, the former is 16x faster than the latter.
So it looks like we should prefer using components, even for simple list items.

Neither of these examples use [React's keys](https://facebook.github.io/react/docs/lists-and-keys.html), how do those affect performance?
Using #1 with Rum's `:with-key` mixin, we get:

| # | timing (ms) |                                                                              |
|---|-------------|------------------------------------------------------------------------------|
| 6 | 18 ± 9      | The list is a component, which creates a keyed component for each list item. |

which is about twice as long as the unkeyed version, #1.
Perhaps this isn't too surprising, since the extra overhead of checking keys has no benefit when nothing gets reordered.
Lets explore what happens when the list items to be rendered are reordered between renders:


| # | timing (ms) |                                                      |
|---|-------------|------------------------------------------------------|
| 7 | 13 ± 7      | #1, but with items randomly shuffled between renders |
| 8 | 18 ± 7      | #6, but with items randomly shuffled between renders |

Well, it again looks like the overhead of keys aren't worth it when re-rendering minimal markup.

The [sablono wiki](https://github.com/r0man/sablono/wiki/Optimization-Tips#avoid-for) suggests avoiding `for`, as it macroexpands into a huge pile of code.
However, at least in the simplest case, the performance of a `for` list comprehension is not different than a lazy `map` or eager `reduce`:


|  # | timing (ms) |                                        |
|----|-------------|----------------------------------------|
| 11 | 7 ± 3       | #1, but with `for` instead of `map`    |
| 12 | 11 ± 4      | #1, but with `reduce` instead of `map` |


## On list event handlers

In my app's list, each item corresponds to an entity in the domain that can be retrieved by id from the application state and queried for changes between renders.
This gives me flexibility in how to set up event handlers.


| # | timing (ms) |                                                                                                                            |
|---|-------------|----------------------------------------------------------------------------------------------------------------------------|
| 3 | 8 ± 5       | #1, with handlers defined as an anonymous function on each item (enclosing the entity via a closure)                       |
| 4 | 6 ± 4       | #1, with a single handler defined on each list item via var, retrieving the item id via data attribute on the clicked item |
| 5 | 6 ± 3       | #1, with a single handler defined on the parent list component, using a similar approach as #4                             |


The latter two seem faster (and I'd expect them to be), but the measurements of all three are indistinguishable within the error bounds.
TODO: Is there a nice way to reliably measure fine memory usage (or, alternatively, bloat the event handlers to improve visibility in the tests?)


## On flat vs. recursive markup

We've seen from #1 that React can happily re-render a flat list of 1000 items in around 7ms.
How does it handle re-rendering nested items?

|  # | timing (ms) |                                            |
|----|-------------|--------------------------------------------|
|  9 | 3 ± 2       | 400 nested components, created recursively |
| 10 | 3 ± 2       | 400 nested components, created w/ loop.    |

Unfortunately, 400 levels was the most I could do before getting "Uncaught RangeError: Maximum call stack size exceeded" errors.
This happened both when defining the nesting recursively (#9) and when invoking the components manually within a loop (#10).
So maybe Sablono or React itself is relying on recursion?

In any case, such nesting is a JS-engine limitation on what we can express in React, since [this StackOverflow post](http://stackoverflow.com/questions/19767745/maximum-level-of-nesting-html-elements) suggests that modern browsers easily handle 10000+ nested elements.



## Misc. notes

React deps are specified in `package.json` because that was the only way I could get `(js/require "react-addons-perf")` to work.
I'm *pretty* sure that the React being used is the one that's pulled in via Rum/Sablono from cljsjs.


## Reading / other projects

+ [js-framework-benchmark](https://github.com/krausest/js-framework-benchmark) is a project similar in spirit to this one, with a ton of different JS libraries. Seems to be for comparing libraries only; I couldn't find any discussion of the results or suggestions for which library/style to use or why. The author does discuss the methodology extensively on [their blog](http://www.stefankrause.net/wp/). Thanks [@thheller](https://github.com/thheller) for the tip!

+ [How to win in web framework benchmarks](https://medium.com/@localvoid/how-to-win-in-web-framework-benchmarks-8bc31af76ce7) details of several virtual DOM library tricks and links to several kinds of benchmarks. Thanks [@dmitriid](https://twitter.com/dmitriid/status/851475009981886464) for the tip!

+ [General CLJS performance tips](https://github.com/alexkehayias/chocolatier#performance) from a game developer. Once I get a handle on all this React stuff, I'd love to put together a suite of perf tests / examples for cljs in general. (But don't wait for me! If you want to dig into this, I'd be happy to chat about it!) Thanks [@shaunlebron](https://github.com/shaunlebron) for the tip!

+ [Sablono hacks](https://gist.github.com/rauhs/38b8598c6549f2fe09ad4d257382ec32) gist demonstrates how to "teach" Sablono to walk more Clojure forms, and thus emit React elements rather than calls to the Sablono runtime interpreter. Arguably, if this kind of thing works reliably, it should be merged upstream.
