This Android application uses multiple factors to determine the safety of the open hotspot which the phone is connected to. These factors are: If a captive portal is present, what is the URL of the portal is and whether or not the captive portal uses an HTTPS connection.  
If you want to add a 'trusted' hotspot to the hashmap you can hardcode it in HotspotInfo.java. The information needed to add the hotspot is the SSID of the hotspot, the captive portal url and the fingerprint of the SSL certificate. Here's an example of how to add one:  
 
mKnownNetworks.put("SSID_Example", new HotspotInfo("SSID_Example", "https://www.example.com/", "1111111111111111111111111111111111111111")); 
 
A possible improvement of this application would be to add a database to it, which keeps track of trusted open hotspots. The database could have the following structure: an auto-incrementing primary key, the SSID, the captive portal URL and a hash of the SSL certificate. The combination of SSID, captive portal URL and the hash of the SSL certificate should be unique in the table. 
CREATE TABLE `magazijn`.`hotspot` ( 
 `hotspot_id` INT NOT NULL AUTO_INCREMENT, 
 `ssid` VARCHAR(45) NOT NULL, 
 `captive_portal_url` VARCHAR(45) NOT NULL, 
 `certificate_fingerprint` VARCHAR(45) NOT NULL, 
 PRIMARY KEY (`hotspot_id`), 
 UNIQUE INDEX `unique_hotspot` (`ssid` ASC, `captive_portal_url` ASC, `certificate_fingerprint` ASC)); 
Another possible improvement would be to better explain the reason behind the score or perhaps a better way to notify the user of the score. 
 
Made by Erik Schamper, Ian Seinstra and Jesper den Boer 
