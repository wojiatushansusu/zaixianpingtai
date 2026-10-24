import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { noAuth: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/views/layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/Profile.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true }
      }
    ]
  },
  {
    path: '/student',
    component: () => import('@/views/layout/Layout.vue'),
    redirect: '/student/exams',
    meta: { roles: ['student', 'admin'] },
    children: [
      {
        path: 'exams',
        name: 'StudentExams',
        component: () => import('@/views/student/StudentExams.vue'),
        meta: { title: '我的考试', icon: 'Tickets' }
      },
      {
        path: 'exam/:examId',
        name: 'TakeExam',
        component: () => import('@/views/student/TakeExam.vue'),
        meta: { title: '在线考试', hideAiAssistant: true, hidden: true }
      },
      {
        path: 'grades',
        name: 'StudentGrades',
        component: () => import('@/views/student/StudentGrades.vue'),
        meta: { title: '我的成绩', icon: 'Medal' }
      },
      {
        path: 'wrong-questions',
        name: 'WrongQuestions',
        component: () => import('@/views/student/WrongQuestions.vue'),
        meta: { title: '错题本', icon: 'Warning' }
      },
      {
        path: 'practice/:practiceId',
        name: 'TakePractice',
        component: () => import('@/views/student/TakePractice.vue'),
        meta: { title: '错题练习', hideAiAssistant: true, hidden: true }
      },
      {
        path: 'self-study',
        name: 'SelfStudyExam',
        component: () => import('@/views/student/SelfStudyExam.vue'),
        meta: { title: 'AI 出卷', icon: 'MagicStick' }
      }
    ]
  },
  {
    path: '/teacher',
    component: () => import('@/views/layout/Layout.vue'),
    redirect: '/teacher/questions',
    meta: { roles: ['teacher', 'admin'] },
    children: [
      {
        path: 'questions',
        name: 'QuestionManage',
        component: () => import('@/views/teacher/QuestionManage.vue'),
        meta: { title: '题库管理', icon: 'Collection' }
      },
      {
        path: 'papers',
        name: 'PaperManage',
        component: () => import('@/views/teacher/PaperManage.vue'),
        meta: { title: '试卷管理', icon: 'Document' }
      },
      {
        path: 'exams',
        name: 'ExamManage',
        component: () => import('@/views/teacher/ExamManage.vue'),
        meta: { title: '考试管理', icon: 'Calendar' }
      },
      {
        path: 'monitor/:examId',
        name: 'MonitorCenter',
        component: () => import('@/views/teacher/MonitorCenter.vue'),
        meta: { title: '监考中心', icon: 'VideoCamera', hidden: true }
      },
      {
        path: 'grading/:examId',
        name: 'SubjectiveGrading',
        component: () => import('@/views/teacher/SubjectiveGrading.vue'),
        meta: { title: '主观题批改', icon: 'EditPen', hidden: true }
      },
      {
        path: 'grades/:examId',
        name: 'GradeView',
        component: () => import('@/views/teacher/GradeView.vue'),
        meta: { title: '成绩分析', icon: 'DataLine', hidden: true }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/views/layout/Layout.vue'),
    redirect: '/admin/users',
    meta: { roles: ['admin'] },
    children: [
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', icon: 'UserFilled' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  const roleKey = userInfo.roleKey

  if (to.meta?.noAuth) {
    if (token && to.path === '/login') {
      next('/dashboard')
    } else {
      next()
    }
    return
  }

  if (!token) {
    next('/login')
    return
  }

  if (to.meta?.roles && to.meta.roles.length > 0) {
    if (to.meta.roles.includes(roleKey)) {
      next()
    } else {
      next('/dashboard')
    }
    return
  }

  next()
})

router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = to.meta.title + ' - 在线考试平台'
  } else {
    document.title = '在线考试平台'
  }
})

export default router
