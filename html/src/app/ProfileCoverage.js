import L from 'leaflet'
import * as utils from 'app/utils'
import * as controls from 'app/controls'

/**
 * A layer that represents all profiles of a dataset and is bound
 * to a single parameter of the profiles.
 * 
 * Only a single global time/vertical is loaded at any point.
 */
export default class ProfileCoverageLayer {
	
	constructor(dataset, param) {
	  this.COVERAGE_LAYER = true
		this.dataset = dataset
		this.param = param
		this._initAxes()
	}
	
	/**
	 * Sets defaults for the initial time/vertical slice that is queried and displayed
	 * when adding the layer to the map.
	 * 
	 * Note that the vertical and time axes behave differently:
	 * 
	 * For the time axis, all profiles between a selected time extent are displayed.
	 * The initial extent is [newest time minus 24 hours, newest time].
	 * 
	 * For the vertical axis, the profiles are filtered by a given vertical extent first
	 * and then a single value of each profile is displayed which is chosen
	 * by being closest to a given vertical target. Typically, the target is the 
	 * middle of the selected vertical extent. 
	 * The initial target is the middle value of the total vertical extent.
	 * The initial extent is [initial target - 100, initial target + 100].
	 */
	_initAxes () {
		const tDefaultWidth = 60 * 60 * 24 * 1000 // 1 day
		const zDefaultWidth = 200
		
		// time axis
		this.tTotalExtent = [new Date(this.dataset.temporal.start),
		                     new Date(this.dataset.temporal.end)]
		const [,tHigh] = this.tTotalExtent
		this.tCurrentExtent = [new Date(tHigh - tDefaultWidth), tHigh]
		
		// vertical axis
		this.zTotalExtent = this.dataset.verticalExtent
		const [zLow,zHigh] = this.zTotalExtent
		this.zCurrentTarget = (zLow + zHigh) / 2
		this.zCurrentExtent = [this.zCurrentTarget - zDefaultWidth / 2, 
		                       this.zCurrentTarget + zDefaultWidth / 2]
	}
	
	set time (middleOrExtent) {
		if (Array.isArray(middleOrExtent)) {
			var extent = middleOrExtent
			extent = [new Date(extent[0]), new Date(extent[1])]
			if (extent[1] < extent[0]) throw new Error('end is before start time')
		} else {
			const middle = new Date(middleOrExtent)
			const width = this.timeDuration
			var extent = [new Date(middle - width / 2), new Date(middle + width / 2)]
		}
		if (extent[0] !== this.tCurrentExtent[0] || 
        extent[1] !== this.tCurrentExtent[1]) {
		  this.tCurrentExtent = extent
		  this._refreshData() 
		}
	}
	
	get time () {
		return this.tCurrentExtent.slice()
	}
	
	set timeDuration (millis) {
		const middle = (this.tCurrentExtent[1] + this.tCurrentExtent[0]) / 2
		this.time = [middle - millis / 2, middle + millis / 2]
	}
	
	get timeDuration () {
		return this.tCurrentExtent[1] - this.tCurrentExtent[0]
	}
	
	/**
	 * Sets the vertical extent and target based on either 
	 * a single target value, an extent, or an object.
	 * 
	 * When a single value is assigned, then this becomes the new vertical
	 * target and the extent is moved accordingly such that the target
	 * is in the middle of it. This should be used together with the
	 * 'verticalWidth' property.
	 * 
	 * When a extent is given (array of start and end value) then this
	 * becomes the new extent where the new target value is the middle of it.
	 * 
	 * When an object is given, then both the target and extent can be
	 * given separately as {target: 100, extent: [100,500]}. 
	 * 
	 */
	set vertical (val) {
	  if (Array.isArray(val)) {
      var extent = val
      var target = (extent[0] + extent[1]) / 2
    } else if (val instanceof Object) {
			var {extent, target} = val
		} else {
			var target = val
			var extent = [target - this.verticalWidth / 2,
			              target + this.verticalWidth / 2]
		}
	  if (target !== this.zCurrentTarget || 
	      extent[0] !== this.zCurrentExtent[0] || 
	      extent[1] !== this.zCurrentExtent[1]) {
	    this.zCurrentExtent = extent
	    this.zCurrentTarget = target
	    this._refreshData()  
	  }
	}
	
	get vertical () {
		return {
			extent: this.zCurrentExtent.slice(),
			target: this.zCurrentTarget
		}
	}
	
	/**
	 * Expands the vertical extent to the given width with the target
	 * in the middle of it. This is intended to be used together
	 * with setting single values for the 'vertical' property.
	 */
	set verticalWidth (width) {
		var extent = [this.zCurrentTarget - width / 2,
		              this.zCurrentTarget + width / 2]
		if (extent[0] !== this.zCurrentExtent[0] || 
        extent[1] !== this.zCurrentExtent[1]) {
		  this.zCurrentExtent = extent
		  this._refreshData()  
		}
	}
	
	get verticalWidth () {
		return this.zCurrentExtent[1] - this.zCurrentExtent[0]
	}
	
	/**
	 * Invalidates any loaded profile data, 
	 * fetches the current data slice, removes the current FeatureGroup layer,
	 * and adds a new FeatureGroup layer with the loaded data.
	 * UI controls are not touched, that is, all sliders and the current palette
	 * will stay as they are.
	 */
	_refreshData () {
		this._invalidateData()
		this._loadDataIfNeeded(updated => {
		  this._removeCoverageLayer()
		  this._addCoverageLayer()
		})
	}
	
	_invalidateData () {
	  if (this.data) {
	    delete this.data
	  }
	}
	
	/**
	 * Loads data for the current time/vertical slice and calls
	 * the callback with true if data has been loaded or false
	 * if data was already loaded.
	 */
	_loadDataIfNeeded (callback) {
	  if (this.data) {
	    callback(false)
	    return
	  }
	  if (this.xhr) {
	    // only handle the latest data request and ignore earlier ones
	    // typically this happens when the user changes the time/vertical
	    // slider quickly several times
	    this.xhr.abort()
	  }
	  const url = this.dataset.features + 
	              '?filter=type=Profile' +
	              '&subset=timeStart=' + this.tCurrentExtent[0].toISOString() +
	                     ';timeEnd=' + this.tCurrentExtent[1].toISOString() +
	                     ';verticalStart=' + this.zCurrentExtent[0] +
	                     ';verticalEnd=' + this.zCurrentExtent[1] +
	                     ';verticalTarget=' + this.zCurrentTarget +
	                     ';params=' + this.param.localId +
	              '&details=domain,range'
    this.xhr = utils.loadBinaryJson(url, data => {
      delete this.xhr
      this.data = data
      callback(true)
    })
	}
	
	_addControls () {
	  // TODO display value of profile over which mouse hovers in legend
	  let unit = this.param.unit ? this.param.unit.label : ''
	  let legend = controls.legend(this._map.palette, this.param.observedProperty.label,
	      this.paletteMin.toFixed(2), this.paletteMax.toFixed(2), this.param.unit.label)
	  this.legend = legend
	  this._map.addControl(legend)
	  
	  let timeSlider = controls.timeSlider(this.tTotalExtent, this.tCurrentExtent,
	      newExtent => {
	        this.time = newExtent
	      })
	  this.timeSlider = timeSlider
	  this._map.addControl(timeSlider)
	  
    let verticalSlider = controls.verticalSlider(this.zTotalExtent, this.zCurrentExtent,
        'm', // TODO derive units from vertical CRS
        newExtent => {
          this.vertical = newExtent
        })
    this.verticalSlider = verticalSlider
    this._map.addControl(verticalSlider)
	}
	
	_removeControls () {
    this._map.removeControl(this.legend)
    delete this.legend
    
    this._map.removeControl(this.timeSlider)
    delete this.timeSlider
    
    this._map.removeControl(this.verticalSlider)
    delete this.verticalSlider
	}
		
	/**
	 * Construct a layer out of all loaded profile data and add it to the map.
	 */
	_addCoverageLayer () {
	  if (this._layer) throw new Error('layer is already added')
	  if (!this.data) throw new Error('no data loaded')
	  
	  let layer = this._createCoverageLayer(this.data.features)
	  this._layer = layer
	  this._map.addLayer(layer)
	}
	
	_createCoverageLayer (features) {
	  let palette = this._map.palette
	  var paletteRed = palette.allowedValues.red
	  var paletteGreen = palette.allowedValues.green
	  var paletteBlue = palette.allowedValues.blue
	  
	  const uomZ = 'm' // TODO derive that from verticalCrs
	  const uom = this.param.unit ? this.param.unit.label : ''
	  
	  var markers = []
	  for (let feature of features) {
	    let coverage = feature.result
	    // TODO reuse code from GridCoverage if possible
	    let lon = coverage.domain.x
	    let lat = coverage.domain.y
	    let time = coverage.domain.time
	    
	    let zIdx = 0
	    let z = coverage.domain.vertical[zIdx]
	    let val = coverage.range[this.param.id].values[zIdx]
	    
	    if (val >= 99999.0 || val == 0) {
	      // TODO temporary workaround until such values are masked server-side
	      continue
	    }

	    // TODO remove code duplication with GridCoverage
	    let valScaled = Math.trunc((255 + 0.9999) * (val - this.paletteMin) / (this.paletteMax - this.paletteMin))
	    let r = paletteRed[valScaled]
	    let g = paletteGreen[valScaled]
	    let b = paletteBlue[valScaled]
	    
	    let strokeBrightness = 0.7

	    let marker = new L.CircleMarker(new L.LatLng(lat, lon), {
	      fillColor: 'rgb(' + r + ',' + g + ',' + b + ')',
	      fillOpacity: 1,
	      radius: 5,
	      stroke: true,
	      opacity: 1,
	      weight: 1,
	      color: 'rgb(' + Math.round(r*strokeBrightness) + ',' + 
	                      Math.round(g*strokeBrightness) + ',' + 
	                      Math.round(b*strokeBrightness) + ')'
	    })
	    marker.bindPopup('<strong>' + feature.title + '</strong><br />' +
	                     'Time: ' + time.slice(0, 10) + ' ' + time.slice(11, 19) + ' UTC<br />' +
	                     'Depth: ' + z.toFixed(2) + ' ' + uomZ + '<br /><br />' +
	                     'Parameter: ' + this.param.observedProperty.label + '<br />' + 
	                     'Value: ' + val.toFixed(2) + ' ' + uom)
	    markers.push(marker)
	  }
	  
	  var group = L.layerGroup(markers)
	  return group
	}
	
	_removeCoverageLayer () {
	  this._map.removeLayer(this._layer)
	  delete this._layer
	}
		
	onAdd (map) {
	  this._map = map
		this._loadDataIfNeeded(updated => {
		  if (updated) {
		    // Only when this layer is added for the first time we
		    // determine the min/max of the range as initial palette range.
		    // Otherwise the palette range is only updated on user request.
		    this.paletteRange = 'global'
		  }		  
		  this._addCoverageLayer()
		  this._addControls()
		})
	}
	
	onRemove (map) {
	  this._removeControls()
		this._removeCoverageLayer()
				
		delete this._map
	}
	
	set paletteRange (type) {
	  const paramId = this.param.id
	  if (type === 'global') {
	    // TODO temporary workaround until 99999.0 and 0 are properly masked server-side
      let vals = this.data.features.map(f => f.result.range[paramId].values[0])
                                   .filter(v => v < 99999.0 && v != 0)
      this.paletteMin = Math.min.apply(Math, vals)
      
      vals = this.data.features.map(f => f.result.range[paramId].values[0])
                               .filter(v => v < 99999.0 && v != 0)
      this.paletteMax = Math.max.apply(Math, vals)
	  } else if (type === 'fov') {
	    // TODO
	  }
	}
	
	get paletteRange () {
	  return [this.paletteMin, this.paletteMax]
	}
}
