/*
   Управление лентой на WS2812 с компьютера + динамическая яркость
   Создано не знаю кем, допилил и перевёл AlexGyver http://alexgyver.ru/
   2017

   Написал под себя https://github.com/KirillMonster
   2024

   Использовалось ядро GyverCore
*/


#include <FastLED.h>

//----------------------НАСТРОЙКИ-----------------------
#define NUM_LEDS 36 + 21 + 36 + 21  // число светодиодов в ленте
#define DI_PIN 4               // пин, к которому подключена лента
#define OFF_TIME 10000            // время (миллисекунды), через которое лента выключится при пропадания сигнала
#define CURRENT_LIMIT 2000     // лимит по току в миллиамперах, автоматически управляет яркостью (пожалей свой блок питания!) 0 - выключить лимит

//----------------------НАСТРОЙКИ-----------------------
unsigned long tmr_splash, off_timer;

#define serialRate 500000                              // скорость связи с ПК
uint8_t prefix[] = { 'A', 'd', 'a' }, i;  // кодовое слово Ada для связи

CRGB leds[NUM_LEDS];       // создаём ленту
boolean led_state = true;  // флаг состояния ленты


void setup() {
  FastLED.addLeds<WS2812, DI_PIN, GRB>(leds, NUM_LEDS);  // инициализация светодиодов
  FastLED.setBrightness(255);

  if (CURRENT_LIMIT > 0) {
    FastLED.setMaxPowerInVoltsAndMilliamps(5, CURRENT_LIMIT);
  } 

  FastLED.clear();
  FastLED.show();

  rainbow();

  Serial.setTimeout(1);
  Serial.begin(serialRate);
}

void check_connection() {
  if (led_state && millis() - off_timer > (OFF_TIME)) {
    Serial.println("check connection");
    led_state = false;
    if (millis() - tmr_splash > 5) {
      tmr_splash = millis();
      rainbow();
    }
  }
}


void rainbow() {
  LEDS.showColor(CRGB(0, 255, 255));
}

void loop() {
  if (!led_state) led_state = true;
  off_timer = millis();

  for (i = 0; i < sizeof prefix; ++i) {
waitLoop:
    while (!Serial.available()) check_connection();
    ;
    if (prefix[i] == Serial.read()) continue;
    i = 0;
    goto waitLoop;
  }

  for (int i = 0; i < NUM_LEDS; i++) {
    byte r, g, b;
    // читаем данные для каждого канала цвета
    
    while (!Serial.available()) check_connection();
    r = Serial.read();
    while (!Serial.available()) check_connection();
    r += Serial.read();

    while (!Serial.available()) check_connection();
    g = Serial.read();
    while (!Serial.available()) check_connection();
    g += Serial.read();

    while (!Serial.available()) check_connection();
    b = Serial.read();
    while (!Serial.available()) check_connection();
    b += Serial.read();

    leds[i].r = r;
    leds[i].g = g;
    leds[i].b = b;
  }
  FastLED.show();  // отображаем цвета
}