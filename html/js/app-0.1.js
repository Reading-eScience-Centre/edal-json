var version = '0.1';
requirejs.config({
    baseUrl: 'js',
    paths: {
		jquery: [
			'https://code.jquery.com/jquery-1.11.3.min.js',
			'ext/jquery-1.11.3.min'
		],
		jquery-ajax-native: [
			'https://cdn.rawgit.com/acigna/jquery-ajax-native/1.0.1/src/jquery-ajax-native.js',
			'ext/jquery-ajax-native-1.0.1'			
		],
        ndarray: 'ext/ndarray-1.0.18',
		leaflet: [
			'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.3/leaflet-src',
			'ext/leaflet-src-0.7.3'
		],
		leaflet-providers: [
			'https://cdnjs.cloudflare.com/ajax/libs/leaflet-providers/1.0.29/leaflet-providers.min',
			'ext/leaflet-providers.min-1.0.29'
		],
		msgpack: 'ext/msgpack-1.0.5',
		main: 'main-' + version,
		palettes: 'palettes-' + version,
		interpolation: 'interpolation-' + version
    }
});

// Start loading the main app file. Put all of
// your application logic in there.
requirejs(['main']);