'use strict';

// This sends the form details to Java. Only a session cookie is kept after sign-in.
const form = document.getElementById('login-form');
const errorMessage = document.getElementById('login-error');
const signIn = document.getElementById('sign-in');
form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (signIn.disabled || !form.reportValidity()) return;
  signIn.disabled = true;
  signIn.textContent = 'Signing in…';
  errorMessage.hidden = true;
  try {
    const values = new FormData(form);
    const response = await fetch('/auth/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-FitTrack-Request': 'true' },
      body: JSON.stringify({ username: values.get('username'), password: values.get('password') })
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'Sign-in failed. Please try again.');
    form.reset();
    location.replace('/');
  } catch (error) {
    errorMessage.textContent = error.message;
    errorMessage.hidden = false;
  } finally {
    signIn.disabled = false;
    signIn.textContent = 'Sign in';
  }
});
