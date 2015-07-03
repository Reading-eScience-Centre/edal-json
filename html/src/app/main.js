import 'leaflet/dist/leaflet.css!'
import './css/style.css!'

import $ from 'jquery'
import 'jquery-ajax-native'
import ndarray from 'ndarray'
// import * as ops from 'ndarray-ops'
import * as opsnull from 'app/ndarray-ops-null'
import msgpack from 'msgpack'
import L from 'leaflet'
import 'leaflet-providers'
import interpolationMethods from 'app/interpolation'
import * as palettes from 'app/palettes'
import * as controls from 'app/controls'
import * as utils from 'app/utils'
import GridCoverageLayer from 'app/TileLayer.GridCoverage'
import ProfileCoverageLayer from 'app/ProfileCoverage'

var map = L.map('map').setView([10, 0], 2)

var baseLayers = {
  'Esri.WorldImagery': 'Esri Satellite',
  'Esri.WorldShadedRelief': 'Esri Shaded Relief',
  'Esri.OceanBasemap': 'Esri Ocean',
  'Esri.WorldGrayCanvas': 'Esri Gray',
  'OpenStreetMap': 'OpenStreetMap',
  'Stamen.Watercolor': 'Stamen Watercolor',
  'NASAGIBS.ViirsEarthAtNight2012': 'VIIRS City Lights 2012'
}
var layerControl = {}
Object.keys(baseLayers).forEach(function (id) {
  var layer = L.tileLayer.provider(id)
  layerControl[baseLayers[id]] = layer
})
layerControl[baseLayers['Esri.WorldImagery']].addTo(map)
var lc = L.control.layers(layerControl).addTo(map)

// TODO is it a good idea just adding stuff to module space?
/*eslint-disable comma-spacing */
palettes.add('gray', palettes.linear(['#FFFFFF', '#000000']))
palettes.add('grayinv', palettes.linear(['#000000', '#FFFFFF']))
palettes.add('rainbow', palettes.linear(['#0000FF', '#00FFFF', '#00FF00', '#FFFF00', '#FF0000']))
palettes.add('blues', palettes.linear(
  ['#f7fbff','#deebf7','#c6dbef','#9ecae1','#6baed6','#4292c6','#2171b5','#08519c','#08306b']))
palettes.add('greens', palettes.linear(
  ['#f7fcf5','#e5f5e0','#c7e9c0','#a1d99b','#74c476','#41ab5d','#238b45','#006d2c','#00441b']))
palettes.add('reds', palettes.linear(
  ['#fff5f0','#fee0d2','#fcbba1','#fc9272','#fb6a4a','#ef3b2c','#cb181d','#a50f15','#67000d']))
palettes.add('GnBu', palettes.linear(
  ['#f7fcf0','#e0f3db','#ccebc5','#a8ddb5','#7bccc4','#4eb3d3','#2b8cbe','#0868ac','#084081']))
palettes.add('YlGnBu', palettes.linear(
  ['#ffffd9','#edf8b1','#c7e9b4','#7fcdbb','#41b6c4','#1d91c0','#225ea8','#253494','#081d58']))
/*eslint-enable comma-spacing */

// default palette, note that map.palette is our own attribute
var defaultPalette = 'blues'
var defaultInterpolation = 'nearestNeighbor'
map.palette = palettes.palettes[defaultPalette]
map.interpolation = interpolationMethods[defaultInterpolation]


controls.paletteSwitcher(palettes, defaultPalette).addTo(map)
controls.paletteRangeAdjuster().addTo(map)
controls.interpolationSwitcher(interpolationMethods, defaultInterpolation).addTo(map)

// the following assumes a rectilinear data grid

var supportedCrs = {
  'http://www.opengis.net/def/crs/OGC/1.3/CRS84': {}
}


/*
 * The plan is the following:
 * The main entry point should be to give the URL to the dataset, 
 * and from there on everything should be automatic.
 * This means, if the dataset contains profiles, there should be
 * layers for each parameter measured by profiles.
 * If there are grids, then each parameter of each grid becomes
 * a separate layer.
 * The initial information of what the dataset contains should
 * only be retrieved from the "parameters", "featureCounts", "featureParameters",
 * and spatiotemporal extent fields of the dataset, that is,
 * no actual feature should be loaded yet.
 * -> TODO for grids, we have to load the basic data of the features though, right?
 * 
 * Each dataset should probably have its own time and vertical slider,
 * however there may be an option to synchronize sliders as far as possible
 * or indeed just have a single visible slider which controls underlying
 * sliders where the current state of the individual sliders can be displayed somehow.
 */


// TODO add support for adding feature collections as single layer (for profiles)
//  -> this makes most sense when server-side filtering can be done on feature types
//      e.g. &type=Profile
function addFeature (url) {
  $.getJSON(url, function (featureData) {
    var result = featureData.result
    if (!(result.domain.crs in supportedCrs)) {
      window.alert('Sorry, only the CRS84 coordinate reference system is currently supported. Yours is: ' + result.domain.crs)
      return
    }
    result.domain.x = new Float64Array(result.domain.x)
    result.domain.y = new Float64Array(result.domain.y)
    utils.wrapLongitudes(result.domain)
    // create a canvas layer for each parameter
    for (let paramId in result.rangeType) {
      let rangeType = result.rangeType[paramId]
      let clazz = getCoverageClass(result.domain.type)
      if (clazz === null) continue
      let layer = new clazz(result, paramId)
      lc.addOverlay(layer, rangeType.title)
    }
  })
}

function getCoverageClass(domainType) {
  if (domainType === 'RegularGrid' || domainType == 'RectilinearGrid') {
    return GridCoverageLayer
  } else if (domainType === 'Profile') {
    return 
  } else {
    console.log(domainType + ' not supported!')
  }
  return null;
}





addFeature('http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/ICEC')
addFeature('http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/TMP')
addFeature('http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/SALTY')
addFeature('http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/M')
addFeature('http://localhost:8182/api/datasets/20100715-UKMO-L4HRfnd-GLOB-v01-fv02-OSTIA.nc/features/analysed_sst')
addFeature('http://localhost:8182/api/datasets/en3_test_data.nc/features/0:7880')
