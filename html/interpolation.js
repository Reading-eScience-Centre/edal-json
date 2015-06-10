function nearestNeighbor(paramArr, lon, lat, domainLon, domainLat, iLonNeighbors, iLatNeighbors) {
	var iLon = Math.abs(lon-domainLon[iLonNeighbors[0]]) < Math.abs(lon-domainLon[iLonNeighbors[1]]) 
			   ? iLonNeighbors[0] : iLonNeighbors[1];
	var iLat = Math.abs(lat-domainLat[iLatNeighbors[0]]) < Math.abs(lat-domainLat[iLatNeighbors[1]]) 
			   ? iLatNeighbors[0] : iLatNeighbors[1];
	return paramArr.get(iLat,iLon);
}

function distance2(x, y, x0, y0) {
	return Math.sqrt((x -= x0) * x + (y -= y0) * y);
}

function distance(lat1, lon1, lat2, lon2) {
	// http://stackoverflow.com/a/27943
	var deg2rad = Math.PI/180;
	var rad2deg = Math.PI*180;
	var dLat = deg2rad*(lat2-lat1);
	var dLon = deg2rad*(lon2-lon1); 
	var a = 
		Math.sin(dLat/2) * Math.sin(dLat/2) +
		Math.cos(deg2rad*(lat1)) * Math.cos(deg2rad*(lat2)) * 
		Math.sin(dLon/2) * Math.sin(dLon/2)
		; 
	var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
	return c;
}

function inverseDistanceWeighted(paramArr, lon, lat, domainLon, domainLat, iLonNeighbors, iLatNeighbors) {
	var exp = 2;
	// uses 16 neighboring points (less at corners)
	// TODO using 16 points (instead of 4) is too compute intensive
	//if (iLonNeighbors[0] > 0) iLonNeighbors.push(iLonNeighbors[0]-1);
	//if (iLonNeighbors[1] < domainLon.length-1) iLonNeighbors.push(iLonNeighbors[1]+1);
	//if (iLatNeighbors[0] > 0) iLatNeighbors.push(iLatNeighbors[0]-1);
	//if (iLatNeighbors[1] < domainLat.length-1) iLatNeighbors.push(iLatNeighbors[1]+1);
	
	var points = new Array(iLonNeighbors.length*iLatNeighbors.length);
	for (var i=0; i<iLonNeighbors.length; i++) {
		for (var j=0; j<iLatNeighbors.length; j++) {
			points[i*iLatNeighbors.length+j] = [iLatNeighbors[j],iLonNeighbors[i]];
		}
	}
	var vals = new Array(points.length);
	var allnull=true;
	for (var i=0; i<points.length; i++) {
		vals[i] = paramArr.get(points[i][0],points[i][1]);
		if (vals[i] !== null) {
			allnull = false;
		}
	}
	if (allnull) {
		return null;
	}
	var invdist = new Array(points.length);
	for (var i=0; i<points.length; i++) {
		invdist[i] = 1/Math.pow(distance(lat, lon, domainLat[points[i][0]], domainLon[points[i][1]]), exp);
	}
	// when distance is 0 (=exact point match), invdist becomes Infinity
	var inf = invdist.indexOf(Infinity);
	if (inf !== -1) return vals[inf];
	
	var num = 0, denom = 0;
	for (var i=0; i<vals.length; i++) {
		if (vals[i] !== null) {
			num += vals[i]*invdist[i];
			denom += invdist[i];
		}
	}
	return num/denom;
}

interpolationMethods = {
	nearestNeighbor: {
		title: "None (nearest neighbor)",
		fn: nearestNeighbor
	},
	idw: {
		title: "Inverse Distance Weighting",
		fn: inverseDistanceWeighted
	}
};