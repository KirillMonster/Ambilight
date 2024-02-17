import com.fazecast.jSerialComm.SerialPort;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;

public class Main {
    static final String CODE_SERIAL = "Ada"; // Кодовое слово для связи, в скетче нужно тоже установить его.
    static final String SERIAL_PORT = "/dev/ttyUSB0"; // Порт к которому подключена Arduino | Windows - "COMx" | Linux - /dev/xxxxxxx
    static final int BAUDRATE = 500000; // Скорость передачи данных в Serial порт. Выставляете одинаковую скорость в скетче и здесь.
    static final int WIDTH = 2560;
    static final int HEIGHT = 1440;
    static final int SIZE = 100; // Количество пикселей для захвата
    static float SATURATION = 0.4f; // Насыщенность цветов

    // Усиление цвета
    static float AMPLIFY_RED = 1.0f;
    static float AMPLIFY_GREEN = 1.0f;
    static float AMPLIFY_BLUE = 1.0f;

    // Количество светодиодов с каждой стороны
    static final int NUMBER_LEDS_BOTTOM = 36;
    static final int NUMBER_LEDS_LEFT = 21;
    static final int NUMBER_LEDS_TOP = 36;
    static final int NUMBER_LEDS_RIGHT = 21;

    // Отношение длины стороны экрана к количеству светодиодов
    static final int PIXEL_TO_LED_RATIO_RIGHT = HEIGHT / NUMBER_LEDS_RIGHT + 1;
    static final int PIXEL_TO_LED_RATIO_TOP = WIDTH / NUMBER_LEDS_TOP + 1;
    static final int PIXEL_TO_LED_RATIO_LEFT = HEIGHT / NUMBER_LEDS_LEFT + 1;
    static final int PIXEL_TO_LED_RATIO_BOTTOM = WIDTH / NUMBER_LEDS_BOTTOM + 1;

    static Robot robot;
    static SerialPort serial_port;

    static void send_colours(String colours) throws InterruptedException {
        byte[] bytes = "Ada".getBytes();
        serial_port.writeBytes(bytes, bytes.length);

        // Можно убрать. Остановил для того чтобы Arduino успевала обрабатывать данные.
        Thread.sleep(2);

        // Отправка цветов на Arduino
        bytes = colours.getBytes();
        serial_port.writeBytes(bytes, bytes.length);
    }

    public static void main(String[] args) throws AWTException, InterruptedException, FileNotFoundException {
        robot = new Robot();

        // Ини порта
        serial_port = SerialPort.getCommPort(SERIAL_PORT);
        serial_port.setComPortParameters(BAUDRATE, Byte.SIZE, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serial_port.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);

        if (!serial_port.openPort()) {
            System.out.println("Не удалось открыть порт");
            return;
        }

        Thread.sleep(3000);

        double start_time = System.currentTimeMillis();
        int counter = 0;

        while (true) {
            // Получаем скрин экрана
            BufferedImage image = screenshot();

            // Получаем средние цвета по сторонам
            String colours = get_medium_colors(image);

            // Отправляем цвета
            send_colours(colours);

            counter++;

            if (System.currentTimeMillis() - start_time >= 1000)  {
                start_time = System.currentTimeMillis();
                System.out.printf("FPS: %d%n", counter);
                counter = 0;
            }
        }
    }

    static BufferedImage screenshot() {
        return robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
    }

    static String processing_channel(int channel) {
        // Так как в ascii таблице наибольщий код это - 127, то есть у нас получается разрешение одного цветового канала - 7 бит.
        // Это ограничение режет качество цвета в 2 раза.
        // Для обхода ограничения мы можем разбивать канал на 2 его составляющие, тем самым сохраним разрешение.
        if (channel >= 254) {
            // Наивысшее число канала мы можем получить это - 127 * 2 = 254, которое почти равняется белому цвету.
            // Его нет смысла разбивать так как мы не сможем получить символ у которого код выше 127.
            return (char) 127 + String.valueOf((char) 127);
        }
        else if (channel > 127) {
            // Разбиваем канал
            return String.valueOf((char)(127)) + (char)(channel - 127);
        } else {
            // Вовзращаем символ нуля и символ канала т.к Arduino ожидает два символа.
            return (char) 0 + String.valueOf(((char) channel));
        }
    }

    static int constrain_channel(int value) {
        // Ограничваем канал от 0 до 255
        return Math.min(Math.max(value, 0), 255);
    }

    static String processing_color(int r, int g, int b) {
        if (AMPLIFY_RED != 1.0f) {
            r = constrain_channel((int) (r * AMPLIFY_RED));
        }

        if (AMPLIFY_GREEN != 1.0f) {
            g = constrain_channel((int) (g * AMPLIFY_GREEN));
        }

        if (AMPLIFY_BLUE != 1.0f) {
            b = constrain_channel((int) (g * AMPLIFY_BLUE));
        }

        if (SATURATION != 0.0f) {
            double gray = 0.2989 * r + 0.5870 * g + 0.1140 * b;
            r += constrain_channel((int) ((r - gray) * SATURATION));
            g += constrain_channel((int) ((g - gray) * SATURATION));
            b += constrain_channel((int) ((b - gray) * SATURATION));
        }

        return processing_channel(r) + processing_channel(g) + processing_channel(b);
    }

    // Данный код вы подстраиваете по себя т.к у каждого свое направление ленты по монитору.
    // В моем случае это так: низ -> лево -> вверх -> право.
    static String get_medium_colors(BufferedImage image) {
        String result = "";

        int counter = 0;
        int medium_red = 0;
        int medium_green = 0;
        int medium_blue = 0;

        // Нижняя сторона
        for (int x = WIDTH - 1; x >= 0; x--) {
            for (int y = HEIGHT - SIZE; y < HEIGHT; y++) {
                int color = image.getRGB(x, y);
                medium_red += (color >> 16) & 255;
                medium_green += (color >> 8) & 255;
                medium_blue += color & 255;
            }
            counter++;
            if (x % PIXEL_TO_LED_RATIO_BOTTOM == 0) {
                int num = counter * SIZE;
                int r = medium_red / num;
                int g = medium_green / num;
                int b = medium_blue / num;
                result = result.concat(processing_color(r, g, b));
                counter = medium_red = medium_green = medium_blue = 0;
            }
        }
        counter = medium_red = medium_green = medium_blue = 0;

        // Левая сторона
        for (int y = HEIGHT - 1; y >= 0; y--) {
            for (int x = 0; x < SIZE; x++) {
                int color = image.getRGB(x, y);
                medium_red += (color >> 16) & 255;
                medium_green += (color >> 8) & 255;
                medium_blue += color & 255;
            }
            counter++;
            if (y % PIXEL_TO_LED_RATIO_LEFT == 0) {
                int num = counter * HEIGHT;
                int r = medium_red / num;
                int g = medium_green / num;
                int b = medium_blue / num;
                result = result.concat(processing_color(r, g, b));
                counter = medium_red = medium_green = medium_blue = 0;
            }
        }
        counter = medium_red = medium_green = medium_blue = 0;

        // Вверхняя сторона
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < SIZE; y++) {
                int color = image.getRGB(x, y);
                medium_red += (color >> 16) & 255;
                medium_green += (color >> 8) & 255;
                medium_blue += color & 255;
            }
            counter++;
            if (x % PIXEL_TO_LED_RATIO_TOP == 0) {
                int num = counter * SIZE;
                int r = medium_red / num;
                int g = medium_green / num;
                int b = medium_blue / num;
                result = result.concat(processing_color(r, g, b));
                counter = medium_red = medium_green = medium_blue = 0;
            }
        }
        counter = medium_red = medium_green = medium_blue = 0;

        // Правая сторона
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = WIDTH - SIZE; x < WIDTH; x++) {
                int color = image.getRGB(x, y);
                medium_red += (color >> 16) & 255;
                medium_green += (color >> 8) & 255;
                medium_blue += color & 255;
            }
            counter++;
            if (y % PIXEL_TO_LED_RATIO_RIGHT == 0) {
                int num = counter * HEIGHT;
                int r = medium_red / num;
                int g = medium_green / num;
                int b = medium_blue / num;
                result = result.concat(processing_color(r, g, b));
                counter = medium_red = medium_green = medium_blue = 0;
            }
        }
        counter = medium_red = medium_green = medium_blue = 0;
        return result;
    }

}
