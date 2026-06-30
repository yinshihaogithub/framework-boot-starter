package com.framework.admin.file;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class FileAdminControllerTest {

    @Test
    void downloadMapsParamErrorToBadRequest() {
        FileAdminController controller = controller(Result.fail(
                ResultCode.PARAM_ERROR.getCode(), "文件ID必须大于0"));

        ResponseEntity<?> response = controller.download(0L);

        assertError(response, HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR.getCode(), "文件ID必须大于0");
    }

    @Test
    void downloadMapsNotFoundToNotFound() {
        FileAdminController controller = controller(Result.fail(
                ResultCode.NOT_FOUND.getCode(), "文件不存在"));

        ResponseEntity<?> response = controller.download(404L);

        assertError(response, HttpStatus.NOT_FOUND, ResultCode.NOT_FOUND.getCode(), "文件不存在");
    }

    @Test
    void downloadMapsServiceErrorToInternalServerError() {
        FileAdminController controller = controller(Result.fail(
                ResultCode.SERVICE_ERROR.getCode(), "文件读取失败"));

        ResponseEntity<?> response = controller.download(9L);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, ResultCode.SERVICE_ERROR.getCode(), "文件读取失败");
    }

    private static FileAdminController controller(Result<ResponseEntity<Resource>> downloadResult) {
        return new FileAdminController(new StubFileAdminService(downloadResult));
    }

    private static void assertError(ResponseEntity<?> response, HttpStatus status, int code, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isInstanceOf(Result.class);
        Result<?> body = (Result<?>) response.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo(code);
        assertThat(body.getMessage()).isEqualTo(message);
    }

    private static class StubFileAdminService extends FileAdminService {

        private final Result<ResponseEntity<Resource>> downloadResult;

        private StubFileAdminService(Result<ResponseEntity<Resource>> downloadResult) {
            super(null, null, null);
            this.downloadResult = downloadResult;
        }

        @Override
        public Result<ResponseEntity<Resource>> download(Long id) {
            return downloadResult;
        }
    }
}
