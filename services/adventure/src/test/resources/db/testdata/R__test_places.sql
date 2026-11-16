-- Test fixture: a handful of real cities in the shape of the GeoNames import.
-- A repeatable Flyway migration on a test-only location, so it loads once per test database.
INSERT INTO places (id, name, ascii_name, admin1, country_code, lat, lng, population) VALUES
  (1269515, 'Jaipur',        'Jaipur',        'Rajasthan',        'IN', 26.91962,   75.78781,  2711758),
  (1269507, 'Jaisalmer',     'Jaisalmer',     'Rajasthan',        'IN', 26.91763,   70.90387,    65471),
  (1275339, 'Mumbai',        'Mumbai',        'Maharashtra',      'IN', 19.07283,   72.88261, 12691836),
  (1273294, 'Delhi',         'Delhi',         'Delhi',            'IN', 28.65195,   77.23149, 10927986),
  (1277333, 'Bengaluru',     'Bengaluru',     'Karnataka',        'IN', 12.97194,   77.59369,  8443675),
  (1264527, 'Chennai',       'Chennai',       'Tamil Nadu',       'IN', 13.08784,   80.27847,  4681087),
  (1259229, 'Pune',          'Pune',          'Maharashtra',      'IN', 18.51957,   73.85535,  3124458),
  (1260607, 'Panaji',        'Panaji',        'Goa',              'IN', 15.49574,   73.82624,    65586),
  (1263968, 'Manāli',        'Manali',        'Himachal Pradesh', 'IN', 32.25740,   77.17481,     8096),
  (1256237, 'Shimla',        'Shimla',        'Himachal Pradesh', 'IN', 31.10442,   77.16662,   173503),
  (1264733, 'Leh',           'Leh',           'Ladakh',           'IN', 34.16504,   77.58402,    30870),
  (1283240, 'Kathmandu',     'Kathmandu',     'Bagmati',          'NP', 27.70169,   85.32060,  1442271),
  (1609350, 'Bangkok',       'Bangkok',       'Bangkok',          'TH', 13.75398,  100.50144,  5104476),
  (2988507, 'Paris',         'Paris',         'Île-de-France',    'FR', 48.85341,    2.34880,  2138551),
  (4717560, 'Paris',         'Paris',         'Texas',            'US', 33.66094,  -95.55551,    24782),
  (2657896, 'Zürich',        'Zurich',        'Zurich',           'CH', 47.36667,    8.55000,   341730),
  (2643743, 'London',        'London',        'England',          'GB', 51.50853,   -0.12574,  8961989),
  (6058560, 'London',        'London',        'Ontario',          'CA', 42.98339,  -81.23304,   346765),
  (5128581, 'New York City', 'New York City', 'New York',         'US', 40.71427,  -74.00597,  8804190),
  (1850147, 'Tokyo',         'Tokyo',         'Tokyo',            'JP', 35.68950,  139.69171,  8336599)
ON CONFLICT (id) DO NOTHING;
