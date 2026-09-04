import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalTime;

public class TestJackson {
    public static void main(String[] args) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            System.out.println("Parse 22:00:00 -> " + mapper.readValue("\"22:00:00\"", LocalTime.class));
            System.out.println("Parse 22:00 -> " + mapper.readValue("\"22:00\"", LocalTime.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
