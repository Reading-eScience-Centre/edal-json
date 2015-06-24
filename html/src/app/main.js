import 'leaflet/dist/leaflet.css!';
import './css/style.css!';

import $ from 'jquery';
import 'jquery-ajax-native';
import ndarray from 'ndarray';
import ops from 'ndarray-ops';
import msgpack from 'msgpack';
import L from 'leaflet';
import 'leaflet-providers';
import interpolation from 'app/interpolation';
import palettes from 'app/palettes';


var map = L.map('map').setView([10, 0], 2);	

var baseLayers = {
	'Esri.WorldImagery': 'Esri Satellite',		
	'Esri.WorldShadedRelief': 'Esri Shaded Relief',
	'Esri.OceanBasemap': 'Esri Ocean',
	'Esri.WorldGrayCanvas': 'Esri Gray',
	'OpenStreetMap': 'OpenStreetMap',
	'Stamen.Watercolor': 'Stamen Watercolor',
	'NASAGIBS.ViirsEarthAtNight2012': 'VIIRS City Lights 2012'
};
var layerControl = {};
Object.keys(baseLayers).forEach(function (id) {
	var layer = L.tileLayer.provider(id);
	layerControl[baseLayers[id]] = layer;
});
layerControl[baseLayers['Esri.WorldImagery']].addTo(map);
var lc = L.control.layers(layerControl).addTo(map);

function redrawLayers(map) {
	map.eachLayer(function (layer) {
		if ('drawTile' in layer) {
			map.removeLayer(layer);
			map.addLayer(layer);
		}
	});
}


// TODO is it a good idea just adding stuff to module space?
palettes.add("gray", palettes.linear(["#FFFFFF", "#000000"]));
palettes.add("grayinv", palettes.linear(["#000000", "#FFFFFF"]));
palettes.add("rainbow", palettes.linear(["#0000FF", "#00FFFF", "#00FF00", "#FFFF00", "#FF0000"]));
palettes.add("blues", palettes.linear(
	["#f7fbff","#deebf7","#c6dbef","#9ecae1","#6baed6","#4292c6","#2171b5","#08519c","#08306b"]));
palettes.add("greens", palettes.linear(
	["#f7fcf5","#e5f5e0","#c7e9c0","#a1d99b","#74c476","#41ab5d","#238b45","#006d2c","#00441b"]));
palettes.add("reds", palettes.linear(
	["#fff5f0","#fee0d2","#fcbba1","#fc9272","#fb6a4a","#ef3b2c","#cb181d","#a50f15","#67000d"]));
palettes.add("GnBu", palettes.linear(
	["#f7fcf0","#e0f3db","#ccebc5","#a8ddb5","#7bccc4","#4eb3d3","#2b8cbe","#0868ac","#084081"]));
palettes.add("YlGnBu", palettes.linear(
	["#ffffd9","#edf8b1","#c7e9b4","#7fcdbb","#41b6c4","#1d91c0","#225ea8","#253494","#081d58"]));

// default palette, note that map.palette is our own attribute
var defaultPalette = 'blues';
var defaultInterpolation = 'nearestNeighbor';
map.palette = palettes.palettes[defaultPalette];
map.interpolation = interpolation.methods[defaultInterpolation];

var paletteSwitcher = L.control({position: 'bottomleft'});
paletteSwitcher.onAdd = function (map) {
	var div = document.importNode($('#template-palettes')[0].content, true);
	div = $('.palettes', div)[0];
	$(".palette-button[data-palette='" + defaultPalette + "']",div).addClass('button-active');
	$('.palette-button', div).click(function() {
		map.palette = palettes.palettes[$(this).data('palette')];
		$('.palette-button').removeClass('button-active');
		$(this).addClass('button-active');
		redrawLayers(map);
	});
	return div;
};
	
var paletteRangeControl = L.control({position: 'bottomleft'});
paletteRangeControl.onAdd = function (map) {
	var div = document.importNode($('#template-palette-range')[0].content, true);
	div = $('.palette-range', div)[0];
	$('.palette-range-button', div).click(function() {
		var action = $(this).data('palette-range');
		map.eachLayer(function (layer) {
			if (!('drawTile' in layer)) return;
			var range = layer.featureResult.range[layer.paramId];
			if (action == "global") {
				range.paletteMin = range.min;
				range.paletteMax = range.max;
			} else if (action == "fov") {
				// first, get current bounding box
				var bounds = map.getBounds();
				var result = layer.featureResult;
				
				var paramRange4D = getParameterRange4D(result.domain, range.values, layer.paramId);
				var subset = horizontalSubset(result.domain, paramRange4D, bounds).range;
				
				// TODO how is time/vertical dimension handled?
				var extent = ndMinMax(subset);
				
				range.paletteMin = extent.min;
				range.paletteMax = extent.max;
				
			} else if (action == "custom") {
				// TODO display popup to set range extent for each layer
			}
		});			
		redrawLayers(map);
	});
	return div;
};

var interpolationSwitcher = L.control({position: 'bottomleft'});
interpolationSwitcher.onAdd = function (map) {
	var div = document.importNode($('#template-interpolation')[0].content, true);
	div = $('.interpolation', div)[0];
	$(".interpolation-button[data-interpolation='" + defaultInterpolation + "']",div).addClass('button-active');
	$('.interpolation-button', div).click(function() {
		map.interpolation = interpolationMethods[$(this).data('interpolation')];
		$('.interpolation-button').removeClass('button-active');
		$(this).addClass('button-active');
		redrawLayers(map);
	});
	return div;
};
paletteSwitcher.addTo(map);
paletteRangeControl.addTo(map);
interpolationSwitcher.addTo(map);

function createLegend(palette, title, low, high, uom) {
	var legend = L.control({position: 'bottomright'});
	
	legend.onAdd = function (map) {
		var div = document.importNode($('#template-legend')[0].content, true);
		div = $('.legend', div)[0];
		$('.legend-title', div).html(title);
		$('.legend-uom', div).html(uom);
		$('.legend-min', div).html(low);
		$('.legend-max', div).html(high);
		
		var colors = palette.allowedValues;
		var gradient = "";
		for (var i=0; i<colors.ncolors; i++) {
			if (i>0) gradient+= ",";
			gradient+= "rgb(" + colors.red[i] + "," + colors.green[i] + "," + colors.blue[i] + ")";
		}
		$('.legend-palette', div).css('background', 
									  'transparent linear-gradient(to top, ' + gradient + ') repeat scroll 0% 0%');
		return div;
	};
	return legend;
}

// the following assumes a rectilinear data grid
	
var supportedCrs = {
	"http://www.opengis.net/def/crs/OGC/1.3/CRS84": {}
};

function horizontalSubset(domain, paramRange4D, bounds) {
	// prepare bounding box
	var southWest = bounds.getSouthWest();
	var northEast = bounds.getNorthEast();
	var lonRange = [domain.lonDiscontinuity, domain.lonDiscontinuity+360];
	// if the map is zoomed out a lot it can span more than 360deg
	if (northEast.lng - southWest.lng > 360) {
		southWest.lng = -180;
		northEast.lng = 180;
	} else {
		southWest.lng = wrapLongitude(southWest.lng, lonRange);
		northEast.lng = wrapLongitude(northEast.lng, lonRange);
	}
	if (northEast.lng < southWest.lng) {
		// unwrap lng after wrapping, so that both are in right order again
		northEast.lng+= 360;
	}
	
	// we only support CRS84 here for now (lon-lat order)
	var domainLon = domain.x;
	var domainLat = domain.y;
	
	// find indices for subsetting
	var iLonStart = indexOfNearest(domainLon, southWest.lng);
	var iLonEnd = indexOfNearest(domainLon, northEast.lng);
	var iLatStart = indexOfNearest(domainLat, southWest.lat);
	var iLatEnd = indexOfNearest(domainLat, northEast.lat);
	
	// subset
	domainLon = domainLon.slice(iLonStart, iLonEnd+1); // could use ndarray here as well
	domainLat = domainLat.slice(iLatStart, iLatEnd+1);
	paramRange4D = paramRange4D.hi(null,null,iLatEnd+1,iLonEnd+1).lo(null,null,iLatStart,iLonStart);
	
	return {
		x: domainLat,
		y: domainLon,
		range: paramRange4D
	};
}

function getParameterRange4D(domain, paramRange) {
	var nX = domain.x.length;
	var nY = domain.y.length;
	var nVertical = 'vertical' in domain ? domain.vertical.length : 1;
	var nTime = 'time' in domain ? domain.time.length : 1;
	
	return ndarray(paramRange, [nTime,nVertical,nY,nX]);
}

function drawTileFn(layer, paramId) {
	function drawTile(canvas, tilePoint, zoom) {
		var ctx = canvas.getContext('2d');
		var tileSize = this.options.tileSize;
		var result = this.featureResult; // our own little cache
		
		var param = result.range[paramId];

		// projection coordinates of top left tile pixel
		var start = tilePoint.multiplyBy(tileSize);
		var startX = start.x;
		var startY = start.y;
		
		// 4D values array (time, vertical, y, x)
		var paramRange4D = getParameterRange4D(result.domain, param.values);
		
		// store for faster access
		// we only support CRS84 here for now (lon-lat order)
		var domainLon = result.domain.x;
		var domainLat = result.domain.y;
		var domainBbox = result.domain.bbox;
		var lonRange = [result.domain.lonDiscontinuity, result.domain.lonDiscontinuity+360];
		var paletteRed = map.palette.allowedValues.red;
		var paletteGreen = map.palette.allowedValues.green;
		var paletteBlue = map.palette.allowedValues.blue;
		var interp = map.interpolation.fn;
		
		var tLoop = Date.now();
		
		
		var bigDomain = false;
		if (bigDomain) {
			// use subset of domain/values based on bounding box
			// will speed up indexOfNearest
			// TODO check if this is really the case for bigger datasets
			// TODO check if this has any effect on quality
			var tileTopLeftGeo = map.unproject(start);
			var tileBottomRightGeo = map.unproject(L.point(startX+tileSize, startY+tileSize));
			var bounds = L.latLngBounds([tileTopLeftGeo, tileBottomRightGeo]);
			var subset = horizontalSubset(result.domain, paramRange4D, bounds);
			domainLon = subset.y;
			domainLat = subset.x;
			paramRange4D = subset.range;
		}
		
		var imgData = ctx.getImageData(0, 0, tileSize, tileSize);
		// Uint8ClampedArray, 1-dimensional, in order R,G,B,A,R,G,B,A,... row-major
		var rgba = ndarray(imgData.data, [tileSize,tileSize,4]);
		
		
		var tNear = 0;
		var tUnproject = 0;
					
		function setPixel(tileY, tileX, val) {
			// map value to color using a palette
			// scale val to [0,255] using the range extent
			// (IDL bytscl formula: http://www.exelisvis.com/docs/BYTSCL.html)
			valScaled = Math.trunc((255 + 0.9999) * (val - param.paletteMin)/(param.paletteMax - param.paletteMin));
			
			rgba.set(tileY,tileX,0,paletteRed[valScaled]);
			rgba.set(tileY,tileX,1,paletteGreen[valScaled]);
			rgba.set(tileY,tileX,2,paletteBlue[valScaled]);
			rgba.set(tileY,tileX,3,255);
		}
		
		// TODO expose time/height choice via UI
		paramRange2D = paramRange4D.pick(0,0,null,null);
		
		function drawAnyProjection() {
			// TODO rewrite with interpolation support like drawMercator
			// usable for any map projection, but computationally more intensive
			
			// TODO there are two hotspots in the loop: map.unproject and indexOfNearest
			
			for (var tileX=0; tileX<tileSize; tileX++) {				
				for (var tileY=0; tileY<tileSize; tileY++) {
					// get geographic coordinates of tile pixel
					var posGeo = map.unproject(L.point(startX+tileX, startY+tileY));
					
					var lat = posGeo.lat,
						lon = posGeo.lng;
					
					// we first check whether the tile pixel is outside the domain bounding box
					// in that case we skip it as we do not want to extrapolate
					if (lat < domainBbox[1] || lat > domainBbox[3]) {
						continue;
					}
						
					lon = wrapLongitude(lon, lonRange);
					if (lon < domainBbox[0] || lon > domainBbox[2]) {
						continue;
					}
					
					// now we find the closest grid cell using simple binary search
					// for finding the closest latitude/longitude we use a simple binary search
					// (as the array is always ascending and there is no discontinuity)
					//var t0 = Date.now();
					var iLatNeighbors = indicesOfNearest(domainLat, lat);
					var iLonNeighbors = indicesOfNearest(domainLon, lon);
					//tNear += Date.now()-t0;
										
					// now we fetch the range value at the given grid cell
					var val = interp(paramRange2D, lon, lat, domainLon, domainLat, iLonNeighbors, iLatNeighbors);
					if (val === null) {
						continue;
					}
					
					setPixel(tileY, tileX, val);
				}
			}
		}
		
		function drawMercator() {
			// optimized version for mercator-like projections
			// this can be used when lat and long can be computed independently for a given pixel
						
			var latCache = new Array(tileSize);
			var iLatNeighborsCache = new Array(tileSize);
			for (var tileY=0; tileY<tileSize; tileY++) {
				var lat = map.unproject(L.point(startX, startY+tileY)).lat;
				latCache[tileY] = lat;
				// find the index of the closest latitude in the grid using simple binary search
				iLatNeighborsCache[tileY] = indicesOfNearest(domainLat, lat);
			}
			
			for (var tileX=0; tileX<tileSize; tileX++) {
				var lon = map.unproject(L.point(startX+tileX, startY)).lng;
				lon = wrapLongitude(lon, lonRange);
				if (lon < domainBbox[0] || lon > domainBbox[2]) {
					continue;
				}
				// find the index of the closest longitude in the grid using simple binary search
				// (as the array is always ascending and there is no discontinuity)
				var iLonNeighbors = indicesOfNearest(domainLon, lon);
				
				for (var tileY=0; tileY<tileSize; tileY++) {
					// get geographic coordinates of tile pixel
					var lat = latCache[tileY];
					
					// we first check whether the tile pixel is outside the domain bounding box
					// in that case we skip it as we do not want to extrapolate
					if (lat < domainBbox[1] || lat > domainBbox[3]) {
						continue;
					}
					
					var iLatNeighbors = iLatNeighborsCache[tileY];
					
					// now we fetch the range value at the given grid cell						
					var val = interp(paramRange2D, lon, lat, domainLon, domainLat, iLonNeighbors, iLatNeighbors);
					if (val === null) {
						continue;
					}
					
					setPixel(tileY, tileX, val);
				}
			}
		}
		
		// TODO check which projection is active and switch if needed
		drawMercator();
		//drawAnyProjection();
		
		//console.log("tile loop, unproject", tUnproject);
		//console.log("tile loop, indexofnearest", tNear);
		//console.log("tile loop", Date.now()-tLoop);
		
		ctx.putImageData(imgData, 0, 0);
	}
	// the following lazy-loads range values
	return function(canvas, tilePoint, zoom) {
		var self = this;
		drawTile.call(self, canvas, tilePoint, zoom);
		// currently we load all data when adding the layer
		// but later we may load the subsetted data for each tile
		/*
		var paramRange = layer.featureResult.range[paramId];
		if ('values' in paramRange) {
			drawTile.call(self, canvas, tilePoint, zoom);
		} else {
			console.log("fetching range");
			$.ajax({
				dataType: 'native',
				accepts: {
					'native': 'application/x-msgpack'
				},
				url: paramRange.id,
				xhrFields: {
				  responseType: 'arraybuffer'
				},
				success: function( raw ) {
					var paramRangeWithValues = msgpack.unpack(new Uint8Array(raw));
					layer.featureResult.range[paramId] = paramRangeWithValues;
					drawTile.call(self, canvas, tilePoint, zoom);
				}
			});
			
		}
		*/
	};
}

function arrayMinMax(arr) {
	var len = arr.length, min = Infinity, max = -Infinity;
	while (len--) {
		var el = arr[len];
		if (el == null) {
			// do nothing
		} else if (el < min) {
			min = el;
		} else if (el > max) {
			max = el;
		}
	}
	return {min: min, max: max};
}

// handle null values in arrays
// ndarray-ops only provides standard argmin and argmax
var compile = require("cwise-compiler");
ops.nullargmin = compile({
  args:["index","array","shape"],
  pre:{
	body:"{this_v=Infinity;this_i=_inline_0_arg2_.slice(0)}",
	args:[
	  {name:"_inline_0_arg0_",lvalue:false,rvalue:false,count:0},
	  {name:"_inline_0_arg1_",lvalue:false,rvalue:false,count:0},
	  {name:"_inline_0_arg2_",lvalue:false,rvalue:true,count:1}
	  ],
	thisVars:["this_i","this_v"],
	localVars:[]},
  body:{
	body:"{if(_inline_1_arg1_ !== null && _inline_1_arg1_<this_v){this_v=_inline_1_arg1_;for(var _inline_1_k=0;_inline_1_k<_inline_1_arg0_.length;++_inline_1_k){this_i[_inline_1_k]=_inline_1_arg0_[_inline_1_k]}}}",
	args:[
	  {name:"_inline_1_arg0_",lvalue:false,rvalue:true,count:2},
	  {name:"_inline_1_arg1_",lvalue:false,rvalue:true,count:2}],
	thisVars:["this_i","this_v"],
	localVars:["_inline_1_k"]},
  post:{
	body:"{return this_i}",
	args:[],
	thisVars:["this_i"],
	localVars:[]}
});

ops.nullargmax = compile({
  args:["index","array","shape"],
  pre:{
	body:"{this_v=-Infinity;this_i=_inline_0_arg2_.slice(0)}",
	args:[
	  {name:"_inline_0_arg0_",lvalue:false,rvalue:false,count:0},
	  {name:"_inline_0_arg1_",lvalue:false,rvalue:false,count:0},
	  {name:"_inline_0_arg2_",lvalue:false,rvalue:true,count:1}
	  ],
	thisVars:["this_i","this_v"],
	localVars:[]},
  body:{
	body:"{if(_inline_1_arg1_ !== null && _inline_1_arg1_>this_v){this_v=_inline_1_arg1_;for(var _inline_1_k=0;_inline_1_k<_inline_1_arg0_.length;++_inline_1_k){this_i[_inline_1_k]=_inline_1_arg0_[_inline_1_k]}}}",
	args:[
	  {name:"_inline_1_arg0_",lvalue:false,rvalue:true,count:2},
	  {name:"_inline_1_arg1_",lvalue:false,rvalue:true,count:2}],
	thisVars:["this_i","this_v"],
	localVars:["_inline_1_k"]},
  post:{
	body:"{return this_i}",
	args:[],
	thisVars:["this_i"],
	localVars:[]}
});

function ndMinMax(arr) {
	var min = arr.get.apply(arr, ops.nullargmin(arr));
	var max = arr.get.apply(arr, ops.nullargmax(arr));
	return {min: min, max: max};
}

function indicesOfNearest(a, x) {
	// return the indices of the two neighbors closest to x 
	// if x exists in the array, both neighbors point to x
	// adapted from https://stackoverflow.com/a/4431347
	var lo = -1, hi = a.length;
	while (hi - lo > 1) {
		var mid = Math.round((lo + hi)/2);
		if (a[mid] <= x) {
			lo = mid;
		} else {
			hi = mid;
		}
	}
	if (a[lo] == x) hi = lo;
	return [lo,hi];
}

function indexOfNearest(a, x) {
	var i = indicesOfNearest(a, x);
	var lo = i[0], hi = i[1];
	if (Math.abs(x-a[lo]) < Math.abs(x-a[hi])) {
		return lo;
	} else {
		return hi;
	}
}

function wrapLongitude(lon, range) {
	return wrapNum(lon, range, true);
}

// stolen from https://github.com/Leaflet/Leaflet/blob/master/src/core/Util.js
// doesn't exist in current release (0.7.3)
function wrapNum(x, range, includeMax) {
	var max = range[1],
		min = range[0],
		d = max - min;
	return x === max && includeMax ? x : ((x - min) % d + d) % d + min;
}

function wrapLongitudes(domain) {
	// we assume WGS84 with lon-lat order (CRS84)
	var containsDiscontinuity = domain.bbox[0] > domain.bbox[2];
	if (containsDiscontinuity) {
		// TODO wrap longitudes to new range without discontinuity
		alert("discontinuity detected, not implemented yet");
	}
	
	domain.lonDiscontinuity = domain.bbox[0];
}

function addFeature(url) {
	$.getJSON(url, function(featureData) {
		var result = featureData.result;
		if (!(result.domain.crs in supportedCrs)) {
			alert("Sorry, only the CRS84 coordinate reference system is currently supported. Yours is: " + result.domain.crs);
			return;
		}
		result.domain.x = new Float64Array(result.domain.x);
		result.domain.y = new Float64Array(result.domain.y);
		wrapLongitudes(result.domain);
		// create a canvas layer for each parameter
		for (var paramId in result.rangeType) {
			var rangeType = result.rangeType[paramId];
			var layer = L.tileLayer.canvas();
			layer.drawTile = drawTileFn(layer, paramId);
			layer.featureResult = result;
			layer.paramId = paramId;
			lc.addOverlay(layer, rangeType.title);
			
			// TODO put this into our own TileLayer class (extending TileLayer.Canvas)
			var originalOnAdd = layer.onAdd;
			layer.onAdd = function (map) {
				var paramRange = layer.featureResult.range[layer.paramId];
				if ('values' in paramRange) {
					calculateExtent(paramRange);
					addLegend(map, layer, rangeType, paramRange);
					originalOnAdd.call(layer, map);
				} else {
					console.time("loadRange");
					$.ajax({
						dataType: 'native',
						accepts: {
							'native': 'application/x-msgpack'
						},
						url: paramRange.id,
						xhrFields: {
						  responseType: 'arraybuffer'
						},
						success: function( raw ) {
							console.timeEnd("loadRange");
							console.time("decodeRange");
							var range = msgpack.unpack(new Uint8Array(raw));
							console.timeEnd("decodeRange");
							layer.featureResult.range[paramId] = range;
							calculateExtent(range);
							addLegend(map, layer, rangeType, range);
							originalOnAdd.call(layer, map);
						}
					});
				}
			};
			
			var originalOnRemove = layer.onRemove;
			layer.onRemove = function (map) {
				map.removeControl(layer.legend);
				originalOnRemove.call(layer, map);
			};
		}
	});
}

// TODO refactor this, put palette info somewhere else
function addLegend(map, layer, rangeType, range) {
	// add legend to map
	var legend = createLegend(map.palette, rangeType.title, 
		range.paletteMin.toFixed(2), range.paletteMax.toFixed(2), rangeType.uom);
	legend.addTo(map);
	layer.legend = legend;
}

function calculateExtent(param) {
	// calculate and cache range extent if not provided
	if (!('min' in param)) {
		var extent = arrayMinMax(param.values);
		param.min = extent.min;
		param.max = extent.max;
	}
	if (!('paletteMin' in param)) {
		param.paletteMin = param.min;
		param.paletteMax = param.max;
	}
}

addFeature("http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/ICEC");
addFeature("http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/TMP");
addFeature("http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/SALTY");
addFeature("http://localhost:8182/api/datasets/foam_one_degree-2011-01-01.nc/features/M");
addFeature("http://localhost:8182/api/datasets/20100715-UKMO-L4HRfnd-GLOB-v01-fv02-OSTIA.nc/features/analysed_sst")
addFeature("http://localhost:8182/api/datasets/en3_test_data.nc/features/0:7880");
