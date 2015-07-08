import L from 'leaflet'
import ndarray from 'ndarray'
import msgpack from 'msgpack'
import * as utils from 'app/utils'
import * as controls from 'app/controls'

// TODO generalize to MultiCoverageLayer?
/**
 * Draws a single parameter of a coverage on a canvas layer.
 * Should be used for gridded coverages, not profiles or other
 * point coverages.
 */
export default class GridCoverageLayer extends L.TileLayer.Canvas {
  
  // TODO we need the surrounding object as well
  //  -> for semantic info like Station ID
  constructor(coverage, paramId) {
    super()
    this.COVERAGE_LAYER = true
    this.coverage = coverage
    this.paramId = paramId
  }
  
  onAdd(map) {
    this._map = map
    var paramRange = this.coverage.range[this.paramId]
    var rangeType = this.coverage.rangeType[this.paramId]
    if ('values' in paramRange) {
      calculateExtent(paramRange)
      addLegend(map, this, rangeType, paramRange)
      super.onAdd(map)
    } else {
      utils.loadBinaryJson(paramRange.id, range => {
        this.coverage.range[this.paramId] = range
        calculateExtent(range)
        addLegend(map, this, rangeType, range)
        super.onAdd(map)
      })
    }
  }
  
  onRemove (map) {
    map.removeControl(this.legend)
    super.onRemove(map)
    delete this._map
  }
  
  set paletteRange (type) {
    var range = this.coverage.range[this.paramId]
    if (type === 'global') {
      range.paletteMin = range.min
      range.paletteMax = range.max
    } else if (type === 'fov') {
      // first, get current bounding box
      var bounds = this._map.getBounds()
      var result = this.coverage

      var paramRange4D = utils.getParameterRange4D(result.domain, range.values, this.paramId)
      var subset = utils.horizontalSubset(result.domain, paramRange4D, bounds).range

      // TODO how is time/vertical dimension handled?
      var extent = utils.minMax(subset)

      range.paletteMin = extent.min
      range.paletteMax = extent.max
    }
  }
  
  get paletteRange () {
    var range = this.coverage.range[this.paramId]
    return [range.paletteMin, range.paletteMax]
  }
  
  drawTile (canvas, tilePoint, zoom) {
    var map = this._map
    var ctx = canvas.getContext('2d')
    var tileSize = this.options.tileSize
    var result = this.coverage // our own little cache

    var param = result.range[this.paramId]

    // projection coordinates of top left tile pixel
    var start = tilePoint.multiplyBy(tileSize)
    var startX = start.x
    var startY = start.y

    // 4D values array (time, vertical, y, x)
    var paramRange4D = utils.getParameterRange4D(result.domain, param.values)

    // store for faster access
    // we only support CRS84 here for now (lon-lat order)
    var domainLon = result.domain.x
    var domainLat = result.domain.y
    var domainBbox = result.domain.bbox
    var lonRange = [result.domain.lonDiscontinuity, result.domain.lonDiscontinuity + 360]
    var paletteRed = map.palette.allowedValues.red
    var paletteGreen = map.palette.allowedValues.green
    var paletteBlue = map.palette.allowedValues.blue
    var interp = map.interpolation.fn

    // var tLoop = Date.now()

    var bigDomain = false
    if (bigDomain) {
      // use subset of domain/values based on bounding box
      // will speed up indexOfNearest
      // TODO check if this is really the case for bigger datasets
      // TODO check if this has any effect on quality
      var tileTopLeftGeo = map.unproject(start)
      var tileBottomRightGeo = map.unproject(L.point(startX + tileSize, startY + tileSize))
      var bounds = L.latLngBounds([tileTopLeftGeo, tileBottomRightGeo])
      var subset = utils.horizontalSubset(result.domain, paramRange4D, bounds)
      domainLon = subset.x
      domainLat = subset.y
      paramRange4D = subset.range
    }

    var imgData = ctx.getImageData(0, 0, tileSize, tileSize)
    // Uint8ClampedArray, 1-dimensional, in order R,G,B,A,R,G,B,A,... row-major
    var rgba = ndarray(imgData.data, [tileSize, tileSize, 4])

    // var tNear = 0
    // var tUnproject = 0

    function setPixel (tileY, tileX, val) {
      // map value to color using a palette
      // scale val to [0,255] using the range extent
      // (IDL bytscl formula: http://www.exelisvis.com/docs/BYTSCL.html)
      var valScaled = Math.trunc((255 + 0.9999) * (val - param.paletteMin) / (param.paletteMax - param.paletteMin))

      rgba.set(tileY, tileX, 0, paletteRed[valScaled])
      rgba.set(tileY, tileX, 1, paletteGreen[valScaled])
      rgba.set(tileY, tileX, 2, paletteBlue[valScaled])
      rgba.set(tileY, tileX, 3, 255)
    }

    // TODO expose time/height choice via UI
    var paramRange2D = paramRange4D.pick(0, 0, null, null)

    function drawAnyProjection () {
      // TODO rewrite with interpolation support like drawMercator
      // usable for any map projection, but computationally more intensive

      // TODO there are two hotspots in the loop: map.unproject and indexOfNearest

      for (let tileX = 0; tileX < tileSize; tileX++) {
        for (let tileY = 0; tileY < tileSize; tileY++) {
          // get geographic coordinates of tile pixel
          let posGeo = map.unproject(L.point(startX + tileX, startY + tileY))

          let lat = posGeo.lat
          let lon = posGeo.lng

          // we first check whether the tile pixel is outside the domain bounding box
          // in that case we skip it as we do not want to extrapolate
          if (lat < domainBbox[1] || lat > domainBbox[3]) {
            continue
          }

          lon = wrapLongitude(lon, lonRange)
          if (lon < domainBbox[0] || lon > domainBbox[2]) {
            continue
          }

          // now we find the closest grid cell using simple binary search
          // for finding the closest latitude/longitude we use a simple binary search
          // (as the array is always ascending and there is no discontinuity)
          // var t0 = Date.now()
          let iLatNeighbors = utils.indicesOfNearest(domainLat, lat)
          let iLonNeighbors = utils.indicesOfNearest(domainLon, lon)
          // tNear += Date.now()-t0

          // now we fetch the range value at the given grid cell
          let val = interp(paramRange2D, lon, lat, domainLon, domainLat, iLonNeighbors, iLatNeighbors)
          if (val === null) {
            continue
          }

          setPixel(tileY, tileX, val)
        }
      }
    }

    function drawMercator () {
      // optimized version for mercator-like projections
      // this can be used when lat and long can be computed independently for a given pixel

      var latCache = new Array(tileSize)
      var iLatNeighborsCache = new Array(tileSize)
      for (let tileY = 0; tileY < tileSize; tileY++) {
        var lat = map.unproject(L.point(startX, startY + tileY)).lat
        latCache[tileY] = lat
        // find the index of the closest latitude in the grid using simple binary search
        iLatNeighborsCache[tileY] = utils.indicesOfNearest(domainLat, lat)
      }

      for (let tileX = 0; tileX < tileSize; tileX++) {
        let lon = map.unproject(L.point(startX + tileX, startY)).lng
        lon = utils.wrapLongitude(lon, lonRange)
        if (lon < domainBbox[0] || lon > domainBbox[2]) {
          continue
        }
        // find the index of the closest longitude in the grid using simple binary search
        // (as the array is always ascending and there is no discontinuity)
        let iLonNeighbors = utils.indicesOfNearest(domainLon, lon)

        for (let tileY = 0; tileY < tileSize; tileY++) {
          // get geographic coordinates of tile pixel
          let lat = latCache[tileY]

          // we first check whether the tile pixel is outside the domain bounding box
          // in that case we skip it as we do not want to extrapolate
          if (lat < domainBbox[1] || lat > domainBbox[3]) {
            continue
          }

          let iLatNeighbors = iLatNeighborsCache[tileY]

          // now we fetch the range value at the given grid cell
          let val = interp(paramRange2D, lon, lat, domainLon, domainLat, iLonNeighbors, iLatNeighbors)
          if (val === null) {
            continue
          }

          setPixel(tileY, tileX, val)
        }
      }
    }

    // TODO check which projection is active and switch if needed
    drawMercator()
    // drawAnyProjection()

    // console.log('tile loop, unproject', tUnproject)
    // console.log('tile loop, indexofnearest', tNear)
    // console.log('tile loop', Date.now()-tLoop)

    ctx.putImageData(imgData, 0, 0)
    
  }
  
}

//TODO refactor this, put palette info somewhere else
function addLegend (map, layer, rangeType, range) {
  // add legend to map
  var legend = controls.legend(map.palette, rangeType.title,
    range.paletteMin.toFixed(2), range.paletteMax.toFixed(2), rangeType.uom)
  legend.addTo(map)
  layer.legend = legend
}

function calculateExtent (param) {
  // calculate and cache range extent if not provided
  if (!('min' in param)) {
    var extent = utils.minMax(param.values)
    param.min = extent.min
    param.max = extent.max
  }
  if (!('paletteMin' in param)) {
    param.paletteMin = param.min
    param.paletteMax = param.max
  }
}