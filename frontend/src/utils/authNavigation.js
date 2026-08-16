const RETURN_PATH_KEY = 'kiwoom.returnPath';

export const saveReturnPath = (path = window.location.pathname + window.location.search) => {
  sessionStorage.setItem(RETURN_PATH_KEY, path);
};

export const restoreReturnPath = () => {
  const returnPath = sessionStorage.getItem(RETURN_PATH_KEY);
  sessionStorage.removeItem(RETURN_PATH_KEY);
  if (!returnPath || returnPath === window.location.pathname + window.location.search) return;
  window.history.replaceState(null, '', returnPath);
  window.dispatchEvent(new PopStateEvent('popstate'));
};

export const redirectToLogin = () => {
  window.location.assign(import.meta.env.DEV ? 'http://localhost:8080/login' : '/login');
};
