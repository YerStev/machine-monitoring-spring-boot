# machine-monitoring-spring-boot

Ein Codeausschnitt aus meinem Hochschulprojekt in Zusammenarbeit mit einem Maschinenhersteller. In dem Projekt wurden Statusänderungen der einzelnen Maschinen per Websocket an das Backend gesendet. Eine der Anforderungen war, dass im Falle einer Maschinenstörung die Benutzer per Push-Benachrichtigung informiert werden sollten. 

Die NotificationService Klasse wird verwendet, wenn eine zu benachrichtigende Zustandsänderung einer Maschine im Backend eintrifft. Diese Klasse verwendet wiederum einen ThreadPool, um die eigentliche Benutzerbenachrichtigung durchzuführen. 

Ich habe mich für diesen Ausschnitt entschieden, da ich die Verwendung eines ThreadPools in diesem Anwendungsfall für sehr sinnvoll halte.
