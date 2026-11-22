set(src_dirs
            LCD
            SPI
            client
            audio
            system_control
            test
            BLE
            button)

set(include_dirs
            LCD
            SPI
            client
            audio
            system_control
            test
            BLE
            button)

set(requires
            driver
            esp_wifi
            esp_http_client
            json
            mbedtls
            nvs_flash
            esp-sr
            bt)

idf_component_register(
    SRC_DIRS ${src_dirs} 
    INCLUDE_DIRS ${include_dirs} 
    REQUIRES ${requires}
    EMBED_FILES "LCD/HZK16.bin")

component_compile_options(-ffast-math -O3 -Wno-error=format=-Wno-format)