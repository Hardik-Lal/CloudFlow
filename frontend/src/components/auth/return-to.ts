const STORAGE_KEY = "cloudflow.returnTo";
const DEFAULT_PATH = "/organizations";

/**
 * Stores where to go after the GitHub round trip. Only same-origin relative paths are accepted so
 * the value cannot be abused as an open redirect.
 */
export function rememberReturnTo(path: string | null) {
  if (isSafePath(path)) {
    sessionStorage.setItem(STORAGE_KEY, path);
  } else {
    sessionStorage.removeItem(STORAGE_KEY);
  }
}

export function consumeReturnTo(): string {
  const path = sessionStorage.getItem(STORAGE_KEY);
  sessionStorage.removeItem(STORAGE_KEY);
  return isSafePath(path) ? path : DEFAULT_PATH;
}

function isSafePath(path: string | null): path is string {
  return !!path && path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\");
}
