package com.semweb.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semweb.map.jena.Record;
import com.semweb.map.jena.Request;
import com.semweb.map.model.Bus;
import com.semweb.map.model.ChoosenCoord;
import com.semweb.map.model.Coordinate;
import com.semweb.map.model.Reponse;
import com.semweb.map.model.ReponseVille;
import com.semweb.map.model.SparqlBusRequestLDModel;
import com.semweb.map.model.SparqlBusRequestLDModel.BusLD;
import com.semweb.map.model.SparqlBusRequestLDUniqueModel;
import com.semweb.map.model.SparqlHospitalRequestLDModel;
import com.semweb.map.model.SparqlHospitalRequestLDModel.HospitalLD;
import com.semweb.map.model.SparqlHospitalRequestLDUniqueModel;
import com.semweb.map.model.SparqlTownRequestModel;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class IndexController {

    /* Gloabl objects used by several Rest Resources */
    ObjectMapper objectMapper = new ObjectMapper();
    Reponse reponse = new Reponse();
    ReponseVille ReponseVille = new ReponseVille("", "no");

    String choosenLat = "0";
    String choosenLon = "0";
    Double baseLat = 45.448007;
    Double baseLon = 4.38609;
    Double currentLat = baseLat;
    Double currentLon = baseLon;
    int currentZoom = 5;

    /*****************/
    /* Rest Resource */
    /*****************/

    /* Index Rest Resource */
    @RequestMapping("/")
    public String index(Model model, Reponse reponse) {
        model.addAttribute("reponse", reponse);
        Record.load();
        return "redirect:/bus";
    }

    /* Data addition on Jena Fuseki Rest Resource */
    @RequestMapping("/index")
    public String add(Model model, Reponse reponse) {
        
        

        return "index";
    }

    /* Hospital Map Rest Resource */
    @RequestMapping("/map")
    public String map(Model model, ReponseVille reponseVille)
            throws JsonParseException, JsonMappingException, IOException {

        model = buildCitiesWithHospitalModel(model, reponseVille);

        ArrayList<HospitalLD> hospitals = new ArrayList<>();
        model = buildHospitalsByCityModel(model, reponseVille, hospitals);

        model = buildHospitalsCoordinatesModel(model, hospitals);

        return "map";

    }

    /* Hospital Map Rest Resource */
    @RequestMapping("/bus")
    public String bus(Model model, ReponseVille reponseVille)
            throws JsonParseException, JsonMappingException, IOException {

        model.addAttribute("currentCoordLat", currentLat);
        model.addAttribute("currentCoordLon", currentLon);

        model.addAttribute("currentZoom", currentZoom);

        model = buildCitiesWithHospitalModel(model, reponseVille);

        ArrayList<HospitalLD> hospitals = new ArrayList<>();
        model = buildHospitalsByCityModel(model, reponseVille, hospitals);

        model = buildHospitalsCoordinatesModel(model, hospitals);

        model = buildNearbyStopsModel(model, reponseVille);

        choosenLat = "0";
        choosenLon = "0";

        currentLat = baseLat;
        currentLon = baseLon;
        currentZoom = 5;

        return "bus";

    }

    /* Data addition on Jena Fuseki Rest Resource */

    @RequestMapping(value = "/getHospitalMarkerCoord", method = RequestMethod.POST)
    public ResponseEntity<String> getHospitalMarkerCoord(Model model, @RequestBody ChoosenCoord choosenCoord) {

        choosenLat = choosenCoord.getLat();
        choosenLon = choosenCoord.getLon();

        currentLat = Double.parseDouble(choosenLat);
        currentLon = Double.parseDouble(choosenLon);
        currentZoom = 12;

        return ResponseEntity.ok(choosenLat);
    }

    /******************/
    /* Model Building */
    /******************/

    /**
     * get the cities with hospital from Jena Fuseki and fill the model to feed the
     * View
     */
    private Model buildCitiesWithHospitalModel(Model model, ReponseVille reponseVille)
            throws JsonMappingException, JsonProcessingException {

        /* Get cities with hospitals */
        SparqlTownRequestModel sparqlTownRequestModel = objectMapper.readValue(Request.getCities(),
                SparqlTownRequestModel.class);
        ArrayList<String> townList = new ArrayList<>();

        for (String city : sparqlTownRequestModel.getCity()) {
            townList.add(city);
        }

        townList.sort(String.CASE_INSENSITIVE_ORDER);
        model.addAttribute("cities", townList);
        model.addAttribute("reponseVille", reponseVille);

        return model;

    }

    /**
     * Retrieves the list of hospitals in the city chosen by the user, sorts it,
     * fills in the template and inserts it in the view.
     */
    private Model buildHospitalsByCityModel(Model model, ReponseVille reponseVille, ArrayList<HospitalLD> hospitals)
            throws JsonMappingException, JsonProcessingException {

        /* Get hospitals by city */
        String city = "Paris";

        if (reponseVille.getName() != null) {
            city = reponseVille.getName();
        }

        Map<String, String> hospitalsList = Request.getHospitalsByCity(city);

        /* Get list with 1 hospital */
        if (Long.valueOf(hospitalsList.get("size")) == 1) {
            SparqlHospitalRequestLDUniqueModel requestHospitalUnique = objectMapper
                    .readValue(hospitalsList.get("content"), SparqlHospitalRequestLDUniqueModel.class);
            HospitalLD uniqueHospital = fillHospitalUnique(requestHospitalUnique);
            hospitals.add(uniqueHospital);
        }
        /* Get list with more than 1 hospital */
        else {
            SparqlHospitalRequestLDModel requestHospitalMultiple = objectMapper.readValue(hospitalsList.get("content"),
                    SparqlHospitalRequestLDModel.class);
            for (HospitalLD hos : requestHospitalMultiple.getGraph()) {
                hospitals.add(hos);
            }
        }

        hospitals.sort(Comparator.comparing(HospitalLD::getName, String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("hospitals", hospitals);

        return model;

    }

    /**
     * Retrieves the list of stops near the hospital chosen by the user, sorts it,
     * fills in the template and inserts it in the view.
     */
    private Model buildNearbyStopsModel(Model model, ReponseVille reponseVille)
            throws JsonMappingException, JsonProcessingException {

        Map<String, String> nearbyList = Request.getNearbyStations(Double.valueOf(choosenLon),
                Double.valueOf(choosenLat));

        ArrayList<Bus> busList = new ArrayList<>();

        if (Long.valueOf(nearbyList.get("size")) != 0) {
            /* Get list with 1 stop */
            if (Long.valueOf(nearbyList.get("size")) == 1) {
                SparqlBusRequestLDUniqueModel requestBusUnique = objectMapper.readValue(nearbyList.get("content"),
                        SparqlBusRequestLDUniqueModel.class);
                Bus bustmp = fillBusUnique(requestBusUnique);
                busList.add(bustmp);
            }
            /* Get list with more than 1 stop */
            else {
                SparqlBusRequestLDModel requestBusMultiple = objectMapper.readValue(nearbyList.get("content"),
                        SparqlBusRequestLDModel.class);
                for (BusLD bus : requestBusMultiple.getGraph()) {
                    Bus bustmp = fillBusMulti(bus);
                    busList.add(bustmp);
                }
            }
        }

        busList.forEach(re -> System.out.println());

        model.addAttribute("busList", busList);

        return model;

    }

    /**
     * removes from the list of hospitals all those who do not have contact
     * information and fills in the template with the contact information
     */
    private Model buildHospitalsCoordinatesModel(Model model, ArrayList<HospitalLD> hospitals) {

        /* Add hospitals coordinate */
        ArrayList<Coordinate> coordList = new ArrayList<>();
        for (HospitalLD hos : hospitals) {
            if (!hos.getLatitude().isEmpty() || !hos.getLongitude().isEmpty()) {
                coordList.add(
                        new Coordinate(Double.parseDouble(hos.getLatitude()), Double.parseDouble(hos.getLongitude())));
            }
        }

        model.addAttribute("coords", coordList);

        return model;
    }

    /*****************/
    /* Utils Methods */
    /*****************/

    /**
     * Filling a hospital structure when there is only one hospital in a city called
     * by {@link #buildHospitalsByCityModel()} in the model building methods
     */
    private HospitalLD fillHospitalUnique(SparqlHospitalRequestLDUniqueModel uniqueHospital) {
        HospitalLD hospital = new HospitalLD(uniqueHospital.getId(), uniqueHospital.getWebsite(),
                uniqueHospital.getAddress(), uniqueHospital.getBedCount(), uniqueHospital.getName(),
                uniqueHospital.getPicture(), uniqueHospital.getWikipedia(), uniqueHospital.getWikipediaArticle(),
                uniqueHospital.getLatitude(), uniqueHospital.getLongitude());
        return hospital;
    }

    /**
     * Filling a bus structure when there is only one bus called by
     * {@link #buildNearbyStopsModel()} in the model building methods
     */
    private Bus fillBusUnique(SparqlBusRequestLDUniqueModel uniqueBus) {
        Bus bus = new Bus(uniqueBus.getId(), uniqueBus.getDescription().getValue(), uniqueBus.getLabel().getValue(),
                uniqueBus.getLatitude(), uniqueBus.getLongitude());
        return bus;
    }

    /**
     * Filling a bus structure when there are many called by
     * {@link #buildNearbyStopsModel()} in the model building methods
     */
    private Bus fillBusMulti(BusLD multiBus) {
        Bus bus = new Bus();

        if (!multiBus.getId().isEmpty() || multiBus.getId() != null) {
            bus.setId(multiBus.getId());
        }

        if (multiBus.getDescription() != null) {
            bus.setDescription(multiBus.getDescription().getValue());
        }

        if (multiBus.getLabel() != null) {
            bus.setLabel(multiBus.getLabel().getValue());
        }

        if (multiBus.getLatitude() != null) {
            bus.setLatitude(multiBus.getLatitude());
        }

        if (multiBus.getLongitude() != null) {
            bus.setLongitude(multiBus.getLongitude());
        }

        return bus;
    }

}
