import 'leaflet/dist/leaflet.css!'
import './css/style.css!'

import 'core-js/es6' // imports all ES6 shims
import 'app/polyfills' // more shims not covered by core-js
import $ from 'jquery'
import 'jquery-ajax-native'
import ndarray from 'ndarray'
import * as opsnull from 'app/ndarray-ops-null'
import msgpack from 'msgpack'
import L from 'leaflet'
import 'leaflet-providers'
import 'leaflet-fullscreen'
import 'leaflet-fullscreen/Control.FullScreen.css!'
import 'leaflet-loading'
import 'leaflet-loading/src/Control.Loading.css!'
import interpolationMethods from 'app/interpolation'
import * as palettes from 'app/palettes'
import * as controls from 'app/controls'
import * as utils from 'app/utils'
import GridCoverageLayer from 'app/TileLayer.GridCoverage'
import ProfileCoverageLayer from 'app/ProfileCoverage'

var map = L.map('map', {
  center: [10, 0],
  zoom: 2,
  fullscreenControl: true,
  fullscreenControlOptions: {
    position: 'topleft'
  },
  loadingControl: true
})

var baseLayers = {
  'Esri.WorldImagery': 'Esri Satellite',
  'Esri.WorldShadedRelief': 'Esri Shaded Relief',
  'Esri.OceanBasemap': 'Esri Ocean',
  'Esri.WorldGrayCanvas': 'Esri Gray',
  'OpenStreetMap': 'OpenStreetMap',
  'Stamen.Watercolor': 'Stamen Watercolor',
  'NASAGIBS.ViirsEarthAtNight2012': 'VIIRS City Lights 2012'
}
// see https://github.com/Leaflet/Leaflet/issues/3599#issuecomment-118918936
var supportsHiDpi = new Set([
   'Esri.WorldImagery',
   'Esri.WorldShadedRelief',
   'Stamen.Watercolor'
   //'NASAGIBS.ViirsEarthAtNight2012' // bug https://github.com/leaflet-extras/leaflet-providers/issues/169
   ])
var layerControl = {}
Object.keys(baseLayers).forEach(function (id) {
  let opt = supportsHiDpi.has(id) ? {detectRetina: true} : {}
  let layer = L.tileLayer.provider(id, opt)
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
//controls.interpolationSwitcher(interpolationMethods, defaultInterpolation).addTo(map)


var supportedCrs = new Set([
  'http://www.opengis.net/def/crs/OGC/1.3/CRS84'
  ])

function initFeature(feature) {
  var result = feature
  if (!supportedCrs.has(result.domain.crs)) {
    //window.alert('Sorry, only the CRS84 coordinate reference system is currently supported. Yours is: ' + result.domain.crs)
    throw result.domain.crs
  }
  result.domain.x = new Float64Array(result.domain.x)
  result.domain.y = new Float64Array(result.domain.y)
  // TODO why did we need to do that again?
  utils.wrapLongitudes(result.domain)
}

function addGridFeature (url) {
  $.getJSON(url, feature => {
    initFeature(feature)
    // create a canvas layer for each parameter
    for (let paramKey of Object.keys(feature.parameters)) {
      addGridFeatureParam(feature, param.id)
    }
  })
}

function addGridFeatureParam (coverages, coverage, paramKey) {
  if (coverages.parameters) {
    var param = coverages.parameters[paramKey]
  } else {
    var param = coverage.parameters[paramKey]
  }  
  let layer = new GridCoverageLayer(coverage, param, paramKey)
  lc.addOverlay(layer, param.observedProperty.label)
}

function addProfileFeatures(dataset, paramKey) {
  let param = dataset.parameters[paramKey]
  let layer = new ProfileCoverageLayer(dataset, param, paramKey)
  lc.addOverlay(layer, param.observedProperty.label)
}

/**
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
 * 
 */
function addDataset (datasetUrl) {
  map.fire('dataloading')
  $.ajax(datasetUrl, {
    dataType: 'json',
    success: dataset => {
      // we need to determine what layers we should add based on the dataset metadata
      let coverageTypes = new Set(dataset.coverageTypes)
      
      if (coverageTypes.has('Profile')) {
        // We assume that all profiles in this dataset belong together.
        // For each parameter a layer is created.
        for (let paramKey of Object.keys(dataset.parameters)) {
          addProfileFeatures(dataset, paramKey)
        }
      }
      
      if (coverageTypes.has('Grid')) {
        // We load all Grid features (without their range) and construct the layers.
        // TODO this loads the domains as well which may be too much in this first step
        map.fire('dataloading')
        let gridFeaturesUrl = dataset.coverages + '?filter=type=Grid&details=domain,rangeMetadata'
        $.ajax(gridFeaturesUrl, {
          dataType: 'json',
          accepts: { json: 'application/prs.coverage+json' },
          success: coverages => {
            for (let coverage of coverages.coverages) {
              initFeature(coverage)
              for (let paramKey of Object.keys(coverage.ranges).filter(k => k !== 'type')) {
                addGridFeatureParam(coverages, coverage, paramKey)
              }
            }
          },
          complete: () => {
            map.fire('dataload')
          }
         })
      }
    },
    complete: () => {
      map.fire('dataload')
    }
  })

}


/*
addGridFeature('http://localhost:8080/api/datasets/foam_one_degree-2011-01-01.nc/features/ICEC')
addGridFeature('http://localhost:8080/api/datasets/foam_one_degree-2011-01-01.nc/features/TMP')
addGridFeature('http://localhost:8080/api/datasets/foam_one_degree-2011-01-01.nc/features/SALTY')
addGridFeature('http://localhost:8080/api/datasets/foam_one_degree-2011-01-01.nc/features/M')
addGridFeature('http://localhost:8080/api/datasets/20100715-UKMO-L4HRfnd-GLOB-v01-fv02-OSTIA.nc/features/analysed_sst')
*/

var host = location.hostname
addDataset('http://' + host + ':8080/api/datasets/foam_one_degree-2011-01-01.nc')
addDataset('http://' + host + ':8080/api/datasets/20100715-UKMO-L4HRfnd-GLOB-v01-fv02-OSTIA.nc')
addDataset('http://' + host + ':8080/api/datasets/01_nemo_test_data.nc')
addDataset('http://' + host + ':8080/api/datasets/en3_test_data.nc')
