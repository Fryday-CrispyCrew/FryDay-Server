package basakan.fryday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FrydayApplication {

	public static void main(String[] args) {
		SpringApplication.run(FrydayApplication.class, args);
	}

}

