// polyfills which are currently not included in core-js

"use strict"

for (var TA of [Int8Array,Uint8Array,Int16Array,Uint16Array,Int32Array,Uint32Array,Float32Array,Float64Array]) {
  if (!TA.prototype.slice) {
    TA.prototype.slice = function (start, end) {
      var len = this.length
      var relativeStart = start
      var k = (relativeStart < 0) ? max(len + relativeStart, 0) : Math.min(relativeStart, len)
      var relativeEnd = (end === undefined) ? len : end
      var final = (relativeEnd < 0) ? max(len + relativeEnd, 0) : Math.min(relativeEnd, len)
      var count = final - k
      var c = this.constructor
      var a = new c(count)
      var n = 0
      while (k < final) {
        var kValue = this[k]
        a[n] = kValue
        ++k
        ++n
      }
      return a
    }
  }
}