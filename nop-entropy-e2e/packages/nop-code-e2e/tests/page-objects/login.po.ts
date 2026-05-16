/**
 * Page Object for the Login page.
 *
 * Thin wrapper around {@link LoginPage} from the shared e2e library,
 * re-exported as `LoginPO` for backward compatibility with existing tests.
 */
import { LoginPage } from '@nop-entropy/e2e-shared';

export class LoginPO extends LoginPage {}

export { LoginPO as default };
