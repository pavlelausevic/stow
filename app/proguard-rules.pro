# SPDX-License-Identifier: Apache-2.0
#
# R8 pun rezim. Pravila su namerno kratka — sto ih je manje, to je manje toga sto se
# tiho zadrzava u APK-u.

# kotlinx.serialization generise serijalizatore kao staticke clanove @Serializable klasa.
# Bez ovoga R8 ih ukloni i izvoz podataka padne tek u runtime-u, na uredjaju korisnika.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclassmembers class **$* implements kotlinx.serialization.KSerializer {
    static <1>$* INSTANCE;
}

# Room generise implementacije DAO-a i baze refleksijom nad imenom klase.
-keep class rs.lausevic.stow.data.db.StowDatabase_Impl { *; }

# Vidzet i precice se instanciraju iz manifesta, pa ih R8 ne vidi kao dosegnute.
-keep class rs.lausevic.stow.widget.TripWidgetReceiver { *; }

# Compose i AndroidX nose svoja pravila kroz consumer-rules; ovde se ne ponavljaju.
