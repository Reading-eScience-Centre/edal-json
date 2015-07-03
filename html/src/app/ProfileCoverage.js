// Idea: use a simple LayerGroup and put CircleMarker items on it
// same as in http://mourner.github.io/Leaflet-Dotter/
// performance should be ok for several thousand circles

import L from 'leaflet'
import msgpack from 'msgpack'
import * as utils from 'app/utils'
import * as controls from 'app/controls'


// TODO needs filtering by time/depth range and target depth

// TODO should we extend the web API to allow filtering by target depth?
//  -> we need to filter by depth range anyway for big datasets and would only
//     expose a single target depth per range, so effectively this reduces data transfer

export default function getProfileCoverageLayer (coverages, paramId) {
  if (!Array.isArray(coverages)) {
    coverages = [coverages]
  }
  
  coverages = coverages.filter(cov => cov.domain.type === 'Profile' && paramId in cov.rangeType)
  
  // calculate extent of paramId over the whole domain
  var min = Math.min.apply(Math, coverages.map(cov => cov.range[paramId].min))
  var max = Math.max.apply(Math, coverages.map(cov => cov.range[paramId].max))
  
  // TODO where do we get the palette from? probably need to do all of this lazily on layer add
  var paletteRed = map.palette.allowedValues.red
  var paletteGreen = map.palette.allowedValues.green
  var paletteBlue = map.palette.allowedValues.blue
  
  var markers = []
  for (let coverage in coverages) {
    // TODO reuse code from GridCoverage if possible
    let lon = coverage.domain.x
    let lat = coverage.domain.y
    let time = coverage.domain.t
    
    // TODO choose specific vertical value
    let zIdx = 0
    let z = coverage.domain.z[zIdx]
    let val = coverage.range[paramId].values[zIdx]

    // TODO remove code duplication with GridCoverage
    let valScaled = Math.trunc((255 + 0.9999) * (val - param.paletteMin) / (param.paletteMax - param.paletteMin))   
    let color = 'rgb(' + paletteRed[valScaled] + ',' + paletteGreen[valScaled] + ',' + paletteBlue[valScaled] + ')'
    
    let marker = new L.CircleMarker(new L.LatLng(lat, lon), {
      fillColor: color,
      fillOpacity: 1,
      radius: 2,
      stroke: false
    })
    marker.bindPopup('time: ' + time + ', vertical: ' + z + ', value: ' + val)
    markers.append(marker)
  }
  
  var group = L.layerGroup(markers)
  return group
}