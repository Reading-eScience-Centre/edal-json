import $ from 'jquery'
import L from 'leaflet'
import * as utils from 'app/utils'

function redrawLayers (map) {
  map.eachLayer(function (layer) {
    if ('drawTile' in layer) {
      map.removeLayer(layer)
      map.addLayer(layer)
    }
  })
}

export function paletteSwitcher (palettes, defaultPaletteKey) {
	var control = L.control({position: 'bottomleft'})
	
	control.onAdd = function (map) {
	  var div = document.importNode($('#template-palettes')[0].content, true)
	  div = $('.palettes', div)[0]
	  $(".palette-button[data-palette='" + defaultPaletteKey + "']", div).addClass('button-active')
	  $('.palette-button', div).click(function () {
	    map.palette = palettes.palettes[$(this).data('palette')]
	    $('.palette-button').removeClass('button-active')
	    $(this).addClass('button-active')
	    redrawLayers(map)
	  })
	  return div
	}
	
	return control
}


export function paletteRangeAdjuster () {
	var control = L.control({position: 'bottomleft'})
	
	control.onAdd = function (map) {
	  var div = document.importNode($('#template-palette-range')[0].content, true)
	  div = $('.palette-range', div)[0]
	  $('.palette-range-button', div).click(function () {
	    var action = $(this).data('palette-range')
	    map.eachLayer(function (layer) {
	      if (!('drawTile' in layer)) return
	      var range = layer.featureResult.range[layer.paramId]
	      if (action === 'global') {
	        range.paletteMin = range.min
	        range.paletteMax = range.max
	      } else if (action === 'fov') {
	        // first, get current bounding box
	        var bounds = map.getBounds()
	        var result = layer.featureResult
	
	        var paramRange4D = utils.getParameterRange4D(result.domain, range.values, layer.paramId)
	        var subset = utils.horizontalSubset(result.domain, paramRange4D, bounds).range
	
	        // TODO how is time/vertical dimension handled?
	        var extent = utils.minMax(subset)
	
	        range.paletteMin = extent.min
	        range.paletteMax = extent.max
	
	      } else if (action === 'custom') {
	        // TODO display popup to set range extent for each layer
	      }
	    })
	    redrawLayers(map)
	  })
	  return div
	}
	return control
}

export function interpolationSwitcher (interpolationMethods, defaultInterpolationKey) {
	var control = L.control({position: 'bottomleft'})

	control.onAdd = function (map) {
	  var div = document.importNode($('#template-interpolation')[0].content, true)
	  div = $('.interpolation', div)[0]
	  $(".interpolation-button[data-interpolation='" + defaultInterpolationKey + "']", div).addClass('button-active')
	  $('.interpolation-button', div).click(function () {
	    map.interpolation = interpolationMethods[$(this).data('interpolation')]
	    $('.interpolation-button').removeClass('button-active')
	    $(this).addClass('button-active')
	    redrawLayers(map)
	  })
	  return div
	}
	return control
}

export function legend (palette, title, low, high, uom) {
  var control = L.control({position: 'bottomright'})

  control.onAdd = function (map) {
    var div = document.importNode($('#template-legend')[0].content, true)
    div = $('.legend', div)[0]
    $('.legend-title', div).html(title)
    $('.legend-uom', div).html(uom)
    $('.legend-min', div).html(low)
    $('.legend-max', div).html(high)

    var colors = palette.allowedValues
    var gradient = ''
    for (var i = 0; i < colors.ncolors; i++) {
      if (i > 0) gradient += ','
      gradient += 'rgb(' + colors.red[i] + ',' + colors.green[i] + ',' + colors.blue[i] + ')'
    }
    $('.legend-palette', div).css('background',
         'transparent linear-gradient(to top, ' + gradient + ') repeat scroll 0% 0%')
    return div
  }
  return control
}
