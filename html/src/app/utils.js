import ndarray from 'ndarray'
import * as opsnull from 'app/ndarray-ops-null'

export function indicesOfNearest (a, x) {
  // return the indices of the two neighbors closest to x
  // if x exists in the array, both neighbors point to x
  // adapted from https://stackoverflow.com/a/4431347
  var lo = -1
  var hi = a.length
  while (hi - lo > 1) {
    let mid = Math.round((lo + hi) / 2)
    if (a[mid] <= x) {
      lo = mid
    } else {
      hi = mid
    }
  }
  if (a[lo] === x) hi = lo
  return [lo, hi]
}

export function indexOfNearest (a, x) {
  var i = indicesOfNearest(a, x)
  var lo = i[0]
  var hi = i[1]
  if (Math.abs(x - a[lo]) < Math.abs(x - a[hi])) {
    return lo
  } else {
    return hi
  }
}

export function wrapLongitude (lon, range) {
  return wrapNum(lon, range, true)
}

// stolen from https://github.com/Leaflet/Leaflet/blob/master/src/core/Util.js
// doesn't exist in current release (0.7.3)
export function wrapNum (x, range, includeMax) {
  var max = range[1]
  var min = range[0]
  var d = max - min
  return x === max && includeMax ? x : ((x - min) % d + d) % d + min
}

export function wrapLongitudes (domain) {
  // we assume WGS84 with lon-lat order (CRS84)
  var containsDiscontinuity = domain.bbox[0] > domain.bbox[2]
  if (containsDiscontinuity) {
    // TODO wrap longitudes to new range without discontinuity
    window.alert('discontinuity detected, not implemented yet')
  }

  domain.lonDiscontinuity = domain.bbox[0]
}

export function horizontalSubset (domain, paramRange4D, bounds) {
  // prepare bounding box
  var southWest = bounds.getSouthWest()
  var northEast = bounds.getNorthEast()
  var lonRange = [domain.lonDiscontinuity, domain.lonDiscontinuity + 360]
  // if the map is zoomed out a lot it can span more than 360deg
  if (northEast.lng - southWest.lng > 360) {
    southWest.lng = -180
    northEast.lng = 180
  } else {
    southWest.lng = wrapLongitude(southWest.lng, lonRange)
    northEast.lng = wrapLongitude(northEast.lng, lonRange)
  }
  if (northEast.lng < southWest.lng) {
    // unwrap lng after wrapping, so that both are in right order again
    northEast.lng += 360
  }

  // we only support CRS84 here for now (lon-lat order)
  var domainLon = domain.x
  var domainLat = domain.y

  // find indices for subsetting
  var iLonStart = indexOfNearest(domainLon, southWest.lng)
  var iLonEnd = indexOfNearest(domainLon, northEast.lng)
  var iLatStart = indexOfNearest(domainLat, southWest.lat)
  var iLatEnd = indexOfNearest(domainLat, northEast.lat)

  // subset
  domainLon = domainLon.slice(iLonStart, iLonEnd + 1) // could use ndarray here as well
  domainLat = domainLat.slice(iLatStart, iLatEnd + 1)
  paramRange4D = paramRange4D.hi(null, null, iLatEnd + 1, iLonEnd + 1)
                             .lo(null, null, iLatStart, iLonStart)

  return {
    x: domainLat,
    y: domainLon,
    range: paramRange4D
  }
}

export function getParameterRange4D (domain, paramRange) {
  var nX = domain.x.length
  var nY = domain.y.length
  var nVertical = 'vertical' in domain ? domain.vertical.length : 1
  var nTime = 'time' in domain ? domain.time.length : 1

  return ndarray(paramRange, [nTime, nVertical, nY, nX])
}

export function minMax (arr) {
  if (Array.isArray(arr)) {
    var len = arr.length
    var min = Infinity
    var max = -Infinity
    while (len--) {
      var el = arr[len]
      if (el == null) {
        // do nothing
      } else if (el < min) {
        min = el
      } else if (el > max) {
        max = el
      }
    }
  } else {
    var min = arr.get.apply(arr, opsnull.nullargmin(arr))
    var max = arr.get.apply(arr, opsnull.nullargmax(arr))
  }
  return {min: min, max: max}
}
