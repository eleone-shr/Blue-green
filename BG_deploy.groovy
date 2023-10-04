def pipelineEnv = ["env":"",
                   "changeRequest":""]
def env = '$(env)'
def buildID = '$(buildID)'
def Recipients = "examplename1@exampleemail.com, examplename2@exampleemail.com"
def goProd = 'false'

def Yamldata = [
    'apiVersion': 'v1',
    'environment': env,
    'version': 1.0,
    'appFamily': 'iep-test',
    'appName': 'bg-sample',
    'buildID': buildID,
    'kind': 'deployment',
    'manifest': 'lines',
    'deployment': [ 
        'target': 'EKS',
        'style': 'non-intrusive',
        'window': 'asap'
    ],
    'email' : 'emails'
]

node {
    stage('Write Yaml') {
        writeYaml file: 'manifest.yaml', data: Yamldata, overwrite: true
        sh 'cat manifest.yaml'
        archiveArtifacts artifacts: "manifest.yaml", fingerprint: true
    }

    stage('Promotion') {
        pipelineEnv = ["env":"",
                      "changeRequest":""]
        goProd = 'false'

        while(pipelineEnv["env"] != 'PROD' && goProd != 'true') {
            pipelineEnv = envSelect()
            if (pipelineEnv["env"] == 'END') {
                echo 'inner'
                break;
            }
            if (pipelineEnv["env"] == 'BLUE-GREEN'){
                echo 'Blue-green deployment selected'
                iepBlueGreen(pipelineEnv)
            }
            if (pipelineEnv["env"] == 'DSIT' || pipelineEnv["env"] == 'SAT' || pipelineEnv["env"] == 'SITE' || pipelineEnv["env"] == 'PTE') {
                echo 'can go to Prod'
                goProd = 'true'
            }

            if (pipelineEnv["env"] == 'PROD' && goProd == 'true') {
                timeout(time: 30, unit: 'MINUTES') {
                    prodApproval()
                }
                echo "Prod deployment approved"
                stageProd()
            }
            if (pipelineEnv["env"] == 'PROD' && goProd != 'true') {
                prodError()
            }
        }
        
    }
}

def iepBlueGreen(pipelineEnv) {
    stage('Blue-Green Deploy') {

        response = input message: "Fill out the following for a blue-green manifest.",
                        id: 'bgresponse',
                        parameters: [string(description: 'Current Version in BG', name: 'blueVersion'),
                                    string(description: 'Updating Version', name: 'greenVersion', defaultValue: "2.0"),
                                    string(description: 'Current Build ID in BG', name: 'blueBuildID', defaultValue: "${BUILD_ID}"),
                                    string(description: 'Updating Build ID', name: 'greenBuildID'),
                                    string(description: 'Blue Weight', name: 'blueWeight'),
                                    string(description: 'Green Weight', name: 'greenWeight'),
                                    choice(name: 'environment',
                                    choices: envTestList)]
        println(response)

       bgCreateManifest(response)     

    }

    stage("BLUE-GREEN Promotion") {
        bgEnvTestList = envTestList.removeElement("BLUE-GREEN")

        while(response["environment"] != PROD) {
            bgResponse = input message: "Select next Environment in B-G Deploy",
                            id: "bgPromotion",
                            parameters: [string(description: 'Adjust the current Blue Weight', name: 'promoBlueWeight', defaultValue: blueWeight ),
                                            string(description: 'Adjust the current Green Weight', name: 'promoGreemWeight', defaultValue: greenWeight),
                                            choice(name: 'ENV',
                                            choices: bgEnvTestList)]
            println(bgResponse)

            bgUpdateManifest(bgResponse)

            if (pipelineEnv["env"] == 'END') {
                echo 'inner'
                break;
            }

            if (pipelineEnv["env"] == 'DSIT' || pipelineEnv["env"] == 'SAT' || pipelineEnv["env"] == 'SITE' || pipelineEnv["env"] == 'PTE') {
                echo 'can go to Prod'
                goProd = 'true'
            }

            if (pipelineEnv["env"] == 'PROD' && goProd == 'true') {
                timeout(time: 30, unit: 'MINUTES') {
                    prodApproval()
                }
                echo "Prod deployment approved"
                stageProd()
            }
            if (pipelineEnv["env"] == 'PROD' && goProd != 'true') {
                prodError()
            }
        }
    }
}

def bgCreateManifest(response) {
    copyArtifacts(projectName: env.JOB_NAME, selector: specific(env.BUILD_NUMBER), filter: 'manifest.yaml')

    def file = readYaml file: "manifest.yaml"

    file['environment'] = response["environment"].toLowerCase()
    file['version'] = response["blueVersion"] + "," + response["greenVersion"]
    file['buildID'] = response["blueBuildID"] + "," + response["greenBuildID"]
    file['kind'] = "promotion"

    file["deployment"]["type"] = "BG"
    file["changeRequest"] = req["changeRequest"]

    def blueWeight = response["blueWeight"].toInteger()
    def greenWeight = response["greenWeight"].toInteger()
    def totalWeight = response["blueWeight"].toInteger() + response["greenWeight"].toInteger()

    while (totalWeight != 100) {
        try { 
            weightExc = input message: "Please re-input Weights correctly",
                                id: "bgWeight",
                                parameters: [string(description: 'Blue Weight', name: 'blueWeight'),
                                            string(description: 'Green Weight', name: 'greenWeight')]
            blueWeight = weightExc["blueWeight"].toInteger()   
            greenWeight = weightExc["greenWeight"].toInteger()
            totalWeight = blueWeight + greenWeight
        } catch (Exception exc) {
            echo "Blue green weight not integers!"    
        }
    }
        file['deployment']["blue"] = ["weight": blueWeight]
        file['deployment']["green"] = ["weight": greenWeight]


    // sh """
    //         if [ -f manifest.yaml ] ; then
    //                 rm -f manifest.yaml
    //         fi
    // """

    writeYaml file: 'manifest.yaml', data: file, overwrite:true


    sh 'cat manifest.yaml'

    archiveArtifacts artifacts: "manifest.yaml", fingerprint: true

    return blueWeight
    return greenWeight

}

def bgUpdateManifest(response) {
    copyArtifacts(projectName: env.JOB_NAME, selector: specific(env.BUILD_NUMBER), filter: 'manifest.yaml')

    def file = readYaml file: "manifest.yaml"

    file['environment'] = response["environment"].toLowerCase()

    file['deployment']["blue"] = ["weight": blueWeight]
    file['deployment']["green"] = ["weight": greenWeight]

    writeYaml file: 'manifest.yaml', data: file, overwrite:true


    sh 'cat manifest.yaml'

    archiveArtifacts artifacts: "manifest.yaml", fingerprint: true

}

def envSelect() {
    stage('Environment Select') {
        envTestList = ["DSIT","SAT","SITE","PTE","PROD","BLUE-GREEN","END"]
            
        req = ""
        
        req = input message: "Select an environment to deploy Artifact?",
            id: 'envResponse',
                parameters: [string(description: 'Change Request number from KISAM', name: 'changeRequest'),
                        choice(name: 'env',
                        choices: envTestList)]
        println(req)

        return req
    }
}

def prodApproval() {
    stage("PROD Approval") {
        def prodApprover = input id: 'iepProdApprover', message: "Approve Deployment to PROD"
    }
}

def stageProd() {
    stage('PROD Deployment') {
        echo "Welcome to PROD"
    }
}

def prodError() {
    stage('Attempted to go to PROD, need to go to SAT/EITE/SITE first') {
        catchError(stageResult: 'FAILURE') {
            sh "exit 1"
        }
        echo 'must go to pre-prod first'
    }
}
                                  
