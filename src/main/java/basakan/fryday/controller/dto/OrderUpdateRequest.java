package basakan.fryday.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderUpdateRequest {

    @NotEmpty(message = "순서 변경할 ID 리스트는 필수입니다.")
    private List<Long> ids;

    public OrderUpdateRequest(List<Long> ids) {
        this.ids = ids;
    }
}
