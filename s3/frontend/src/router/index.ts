import { createRouter, createWebHashHistory } from 'vue-router'
import WeatherView from '../views/WeatherView.vue'
import AboutView from '../views/AboutView.vue'
import QueueView from '../views/QueueView.vue'

/** Hash-history router: deep links work on plain S3 website hosting. */
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'weather', component: WeatherView },
    { path: '/about', name: 'about', component: AboutView },
    { path: '/queue', name: 'queue', component: QueueView },
  ],
})