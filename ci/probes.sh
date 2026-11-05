#!/bin/bash
# Checks that every web source used by HMZ Play answers with the fields the app reads.
OUT=out; mkdir -p $OUT; R=$OUT/probes.txt
p() { echo "=== $1"; }
{
p "OpenAgenda MEL (events)"
TODAY=$(date +%F); TOM=$(date -d tomorrow +%F)
W="within_distance(location_coordinates, geom'POINT(3.06 50.63)', 25km) and firstdate_begin <= '$TOM' and lastdate_end >= '$TODAY'"
curl -sG "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/evenements-publics-openagenda/records" \
  --data-urlencode "limit=100" --data-urlencode "order_by=firstdate_begin" --data-urlencode "where=$W" \
  | jq -r '"total=\(.total_count) err=\(.error_code // "")", (.results[]? | "\(.firstdate_begin) → \(.lastdate_end) | \(.title_fr) | \(.location_name), \(.location_city) | \(.location_coordinates)")' | head -60
p "LOSC (TheSportsDB)"
curl -s "https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id=133711" | jq -r '.events[]? | "\(.strTimestamp) | \(.strEvent) | \(.strVenue) | \(.strLeague)"'
p "SNCF (no key: expect 401)"
curl -s -o /dev/null -w "%{http_code}\n" "https://api.sncf.com/v1/coverage/sncf/stop_areas/stop_area:SNCF:87223263/arrivals?count=1"
p "AirLabs (no key: expect error json)"
curl -s "https://airlabs.co/api/v9/schedules?arr_iata=LIL&api_key=x" | head -c 300; echo
p "Weather open-meteo"
curl -s "https://api.open-meteo.com/v1/forecast?latitude=50.630&longitude=3.060&current=temperature_2m,weather_code&hourly=precipitation_probability&forecast_hours=3" | jq -c '.current, .hourly.precipitation_probability'
p "Fuel prices"
curl -sG "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records" \
  --data-urlencode "limit=3" --data-urlencode "where=within_distance(geom, geom'POINT(3.06 50.63)', 8km)" \
  | jq -c '"total=\(.total_count)", (.results[]? | {adresse, ville, geom, e10_prix, gazole_prix, sp98_prix})'
p "Overpass speed limit"
curl -sG "https://overpass-api.de/api/interpreter" --data-urlencode "data=[out:json][timeout:8];way(around:25,50.6292,3.0573)[highway][maxspeed];out tags 3;" | jq -c '[.elements[]?.tags.maxspeed]'
p "Updater (GitHub latest release)"
curl -s https://api.github.com/repos/hmz-59/hmz-play/releases/latest | jq -r '.tag_name, (.assets[]?.name)'
p "Waze page on APKMirror (HTTP code; 403 = anti-robot, page exists for humans)"
curl -s -o /dev/null -w "%{http_code}\n" -A "Mozilla/5.0 (Linux; Android 9)" "https://www.apkmirror.com/apk/waze/waze-gps-maps-traffic-alerts-live-navigation/waze-navigation-live-traffic-5-9-5-4-release/waze-navigation-live-traffic-5-9-5-4-android-apk-download/"
} > $R 2>&1
cat $R
