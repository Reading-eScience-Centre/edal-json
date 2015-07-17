import $ from 'jquery'
import L from 'leaflet'
// jqrangeslider not yet on JSPM registry
import 'jquery-ui'
import 'jquery-mousewheel'
import 'app/jqrangeslider/jQAllRangeSliders-min'
import 'app/jqrangeslider/iThing-min.css!'
//import 'jQRangeSlider'
import * as utils from 'app/utils'

function redrawLayers (map, tilesOnly=false) {
  map.eachLayer(function (layer) {
    if (!layer.COVERAGE_LAYER) return
    if (tilesOnly) {
      // will not refresh legends etc.
      layer.redraw()
    } else {
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
	  L.DomEvent.disableClickPropagation(div)
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
	  L.DomEvent.disableClickPropagation(div)
	  $('.palette-range-button', div).click(function () {
	    var action = $(this).data('palette-range')
	    map.eachLayer(function (layer) {
	      if (!layer.COVERAGE_LAYER) return
	      
	      if (action === 'custom') {
	        // ask for values and convert to array
	      }
	      
	      layer.paletteRange = action
	    })
	    // TODO this should probably be handled by the layers
	    // -> think about coordinated palettes
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
	  L.DomEvent.disableClickPropagation(div)
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

export function timeSlider (totalExtent, currentExtent, onchange) {
  var control = L.control({position: 'topleft'})

  function pad (n) {
    return n < 10 ? '0' + n : n
  }
  function dateFormatter (d) {
    return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) + ' ' +
           pad(d.getHours()) + ':' + pad(d.getMinutes())
  }
  
  // When totalExtent is e.g. [2000-01-01T12:40:00,2000-01-31T12:50:00] we need
  // to round to the nearest step so that no times are left out.
  // In this case we want [2000-01-01T12:00:00,2000-01-31T13:00:00] with step=1h.
  let totalExtentMin = new Date(totalExtent[0])
  let totalExtentMax = new Date(totalExtent[1])
  totalExtentMin.setMinutes(0, 0, 0)
  if (totalExtentMax.getMinutes() > 0 || totalExtentMax.getSeconds() > 0) {
    totalExtentMax.setHours(totalExtentMax.getHours() + 1, 0, 0)
  }
  
  control.onAdd = map => {
    var div = document.importNode($('#template-time-slider')[0].content, true)
    div = $('.time-slider', div)[0]
    L.DomEvent.disableClickPropagation(div)
    
    $(div).dateRangeSlider({
      bounds: {min: totalExtentMin, max: totalExtentMax},
      defaultValues: {min: currentExtent[0],  max: currentExtent[1]},
      range: {max: {months: 1}},
      step: {hours: 1},
      formatter: dateFormatter,
      arrows: false
    })
    
    $(div).bind('valuesChanged', (e, data) => {
      onchange([data.values.min, data.values.max])
    })
    
    return div
  }
  return control
}

export function verticalSlider (totalExtent, currentExtent, uom, onchange) {
  var control = L.control({position: 'topleft'})
  
  const step = 100
  
  // When totalExtent is e.g. [-1750,1045] we need to round to the nearest step
  // so that no values are left out. In this case we want [-1800,1100] with step=100.
  let totalExtentMin = Math.floor(totalExtent[0] / step) * step
  let totalExtentMax = Math.ceil(totalExtent[1] / step) * step
 
  control.onAdd = map => {
    var div = document.importNode($('#template-vertical-slider')[0].content, true)
    div = $('.vertical-slider', div)[0]
    L.DomEvent.disableClickPropagation(div)
    
    $(div).rangeSlider({
      bounds: {min: totalExtentMin, max: totalExtentMax},
      defaultValues: {min: currentExtent[0],  max: currentExtent[1]},
      step: step,
      formatter: v => v + uom,
      arrows: false
    })
    
    $(div).bind('valuesChanged', (e, data) => {
      onchange([data.values.min, data.values.max])
    })
    
    return div
  }
  return control
}